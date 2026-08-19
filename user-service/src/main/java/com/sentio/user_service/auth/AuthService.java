package com.sentio.user_service.auth;

import com.lisovskyi.security.autoconfigure.security.jwt.JwtBlacklistService;
import com.lisovskyi.security.autoconfigure.security.jwt.JwtService;
import com.lisovskyi.security.autoconfigure.security.jwt.OpaqueTokenService;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceAlreadyExistsException;
import com.lisovskyi.web.error.autoconfigure.standard.UnauthorizedException;
import com.sentio.user_service.auth.dto.*;
import com.sentio.user_service.auth.oauth.GoogleAccountResolver;
import com.sentio.user_service.auth.oauth.dto.GoogleIdentity;
import com.sentio.user_service.auth.token.TokenIssuer;
import com.sentio.user_service.organization.OrganizationConstants;
import com.sentio.user_service.organization.entity.OrganizationMember;
import com.sentio.user_service.organization.enums.PlanTier;
import com.sentio.user_service.organization.service.OrganizationCreationService;
import com.sentio.user_service.organization.service.OrganizationService;
import com.sentio.user_service.refresh_token.RefreshToken;
import com.sentio.user_service.refresh_token.RefreshTokenRepository;
import com.sentio.user_service.user.entity.User;
import com.sentio.user_service.user.mapper.UserMapper;
import com.sentio.user_service.user.repository.UserRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the auth use cases (local register/login, Google sign-in, refresh, logout).
 * Delegates the actual mechanics to focused collaborators: {@link OrganizationCreationService}
 * (creating/joining an organization), {@link OrganizationService} (looking up an existing
 * membership - it owns organization/membership state, this class never touches it directly), {@link
 * GoogleAccountResolver} (who is this Google user) and {@link TokenIssuer} (JWT + refresh token
 * issuance) - this class only wires them together and decides which one to call.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private static final String INVALID_ERROR_MSG = "Invalid email or password";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    private final JwtService jwtService;
    private final OpaqueTokenService opaqueTokenService;
    private final JwtBlacklistService jwtBlacklistService;
    private final OrganizationService organizationService;
    private final AuthGuards authGuards;

    private final TokenIssuer tokenIssuer;
    private final PasswordEncoder passwordEncoder;
    private final OrganizationCreationService organizationProvisioning;
    private final GoogleAccountResolver googleAccountResolver;

    private final UserMapper userMapper;

    @Transactional
    public AuthResult register(RegistrationRequest request, String ip, String userAgent) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResourceAlreadyExistsException("User with email: " + request.email() + " already exists");
        }

        User user = buildLocalUser(request);
        userRepository.save(user);
        user.getIdentities().add(userMapper.toLocalIdentity(user));

        // Always org-less - the user creates or joins an organization as a separate
        // onboarding step (POST /organizations, or accepting an invite), not here.
        return new AuthResult(
                tokenIssuer.issue(user, null, ip, userAgent), userMapper.toUserContextResponse(user, null));
    }

    @Transactional
    public AuthResult login(LoginRequest request, String ip, String userAgent) {
        User user = userRepository
                .findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException(INVALID_ERROR_MSG));

        authGuards.assertNotDeleted(user, INVALID_ERROR_MSG);

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new UnauthorizedException(INVALID_ERROR_MSG);
        }

        OrganizationMember membership =
                organizationService.findDefaultMembership(user.getId()).orElse(null);
        return new AuthResult(
                tokenIssuer.issue(user, membership, ip, userAgent), userMapper.toUserContextResponse(user, membership));
    }

    @Transactional
    public AuthResult loginOrRegisterWithGoogle(GoogleIdentity identity, String ip, String userAgent) {
        User user = googleAccountResolver.resolveOrCreate(identity);

        authGuards.assertNotDeleted(user);

        // A user that already had a default membership (existing identity, or an
        // account we just linked) keeps it. A brand new user has none yet - give
        // them ownership of a brand new org, same as local OWNER registration.
        OrganizationMember membership = organizationService
                .findDefaultMembership(user.getId())
                .orElseGet(() -> organizationProvisioning.createOwnerMembership(
                        user, defaultOrgName(identity), null, PlanTier.SOLO));

        return new AuthResult(
                tokenIssuer.issue(user, membership, ip, userAgent), userMapper.toUserContextResponse(user, membership));
    }

    // Google doesn't always return family_name (and in principle could omit given_name
    // too) - naive concatenation would silently produce names like "John null".
    private String defaultOrgName(GoogleIdentity identity) {
        String firstName = identity.firstName() != null ? identity.firstName() : "";
        String lastName = identity.lastName() != null ? identity.lastName() : "";
        String name = (firstName + " " + lastName).trim();
        String orgName = name.isBlank() ? identity.email() : name;

        // Profile-derived, not user-typed - nothing stops Google from handing back a
        // name longer than the organizations.name column (Organization.NAME_LENGTH).
        return orgName.length() > OrganizationConstants.NAME_LENGTH
                ? orgName.substring(0, OrganizationConstants.NAME_LENGTH)
                : orgName;
    }

    @Transactional
    public AuthTokens refresh(String rawToken, String ip, String userAgent) {
        String hashedToken = opaqueTokenService.hash(rawToken);
        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenHash(hashedToken)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (refreshToken.getRevokedAt() != null) {
            throw new UnauthorizedException("Refresh token has been revoked");
        }
        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Refresh token has expired");
        }

        User user = refreshToken.getUser();
        authGuards.assertNotDeleted(user);

        OrganizationMember membership =
                organizationService.findDefaultMembership(user.getId()).orElse(null);

        AuthTokens authTokens = tokenIssuer.issue(user, membership, ip, userAgent);
        refreshToken.setRevokedAt(Instant.now());
        refreshTokenRepository.save(refreshToken);

        return authTokens;
    }

    @Transactional
    public void logout(String accessToken, String refreshToken) {
        if (refreshToken != null) {
            String hashedToken = opaqueTokenService.hash(refreshToken);

            refreshTokenRepository.findByTokenHash(hashedToken).ifPresent(rt -> {
                rt.setRevokedAt(Instant.now());
                refreshTokenRepository.save(rt);
            });
        }

        if (accessToken != null) {
            try {
                jwtBlacklistService.addToBlacklist(
                        accessToken, jwtService.extractExpiration(accessToken).toEpochMilli());
            } catch (Exception e) {
                log.debug("Could not blacklist access token on logout, skipping", e);
            }
        }
    }

    private User buildLocalUser(RegistrationRequest request) {
        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .lastName(request.lastName())
                .firstName(request.firstName())
                .build();

        if (request.phoneNumber() != null && !request.phoneNumber().isBlank()) {
            user.setPhoneNumber(request.phoneNumber());
        }
        if (request.middleName() != null && !request.middleName().isBlank()) {
            user.setMiddleName(request.middleName());
        }

        return user;
    }
}

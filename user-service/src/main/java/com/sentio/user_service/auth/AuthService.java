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
import com.sentio.user_service.organization.service.OrganizationProvisioningService;
import com.sentio.user_service.organization.service.OrganizationService;
import com.sentio.user_service.organization.entity.OrganizationMember;
import com.sentio.user_service.organization.enums.OrgRole;
import com.sentio.user_service.organization.enums.PlanTier;
import com.sentio.user_service.refresh_token.RefreshToken;
import com.sentio.user_service.refresh_token.RefreshTokenRepository;
import com.sentio.user_service.user.entity.User;
import com.sentio.user_service.user.mapper.UserMapper;
import com.sentio.user_service.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Orchestrates the auth use cases (local register/login, Google sign-in,
 * refresh, logout). Delegates the actual mechanics to focused collaborators:
 * {@link OrganizationProvisioningService} (creating/joining an organization),
 * {@link OrganizationService} (looking up an existing membership - it owns
 * organization/membership state, this class never touches it directly),
 * {@link GoogleAccountResolver} (who is this Google user) and {@link
 * TokenIssuer} (JWT + refresh token issuance) - this class only wires them
 * together and decides which one to call.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private static final String INVALID_VALUE_MSG = "Invalid email or password";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    private final JwtService jwtService;
    private final OpaqueTokenService opaqueTokenService;
    private final JwtBlacklistService jwtBlacklistService;
    private final OrganizationService organizationService;

    private final TokenIssuer tokenIssuer;
    private final PasswordEncoder passwordEncoder;
    private final OrganizationProvisioningService organizationProvisioning;
    private final GoogleAccountResolver googleAccountResolver;

    private final UserMapper userMapper;


    @Transactional
    public AuthResult register(RegistrationRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResourceAlreadyExistsException("User with email: " + request.email() + " already exists");
        }

        User user = buildLocalUser(request);
        userRepository.save(user);
        user.getIdentities().add(userMapper.toLocalIdentity(user));

        OrganizationMember membership = null;
        if (request.name() != null && !request.name().isBlank()) {
            membership = organizationProvisioning.createOwnerMembership(user, request.name(), request.edrpou(), request.plan());
        }

        return new AuthResult(tokenIssuer.issue(user, membership), userMapper.toResponse(user, membership));
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException(INVALID_VALUE_MSG));

        if (user.getDeletedAt() != null) {
            throw new UnauthorizedException(INVALID_VALUE_MSG);
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new UnauthorizedException(INVALID_VALUE_MSG);
        }

        OrganizationMember membership = organizationService.getDefaultMembership(user.getId());
        return new AuthResult(tokenIssuer.issue(user, membership), userMapper.toResponse(user, membership));
    }

    @Transactional
    public AuthResult loginOrRegisterWithGoogle(GoogleIdentity identity) {
        User user = googleAccountResolver.resolveOrCreate(identity);

        // A user that already had a default membership (existing identity, or an
        // account we just linked) keeps it. A brand new user has none yet - give
        // them ownership of a brand new org, same as local OWNER registration.
        OrganizationMember membership = organizationService.findDefaultMembership(user.getId())
                .orElseGet(() -> organizationProvisioning.createOwnerMembership(
                        user, defaultOrgName(identity), null, PlanTier.SOLO));

        return new AuthResult(tokenIssuer.issue(user, membership), userMapper.toResponse(user, membership));
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
    public AuthTokens refresh(String rawToken) {
        String hashedToken = opaqueTokenService.hash(rawToken);
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hashedToken)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (refreshToken.getRevokedAt() != null) {
            throw new UnauthorizedException("Refresh token has been revoked");
        }
        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Refresh token has expired");
        }

        User user = refreshToken.getUser();
        OrganizationMember membership = organizationService.getDefaultMembership(user.getId());

        AuthTokens authTokens = tokenIssuer.issue(user, membership);
        refreshToken.setRevokedAt(Instant.now());
        refreshTokenRepository.save(refreshToken);

        return authTokens;
    }

    @Transactional
    public void logout(String accessToken, String refreshToken) {
        if (refreshToken != null) {
            String hashedToken = opaqueTokenService.hash(refreshToken);

            refreshTokenRepository.findByTokenHash(hashedToken)
                    .ifPresent(rt -> {
                        rt.setRevokedAt(Instant.now());
                        refreshTokenRepository.save(rt);
                    });
        }

        if (accessToken != null) {
            try {
                jwtBlacklistService.addToBlacklist(accessToken, jwtService.extractExpiration(accessToken).toEpochMilli());
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

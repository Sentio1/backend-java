package com.sentio.user_service.auth;

import com.lisovskyi.security.autoconfigure.security.jwt.JwtBlacklistService;
import com.lisovskyi.security.autoconfigure.security.jwt.JwtService;
import com.lisovskyi.security.autoconfigure.security.jwt.OpaqueTokenService;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceAlreadyExistsException;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.lisovskyi.web.error.autoconfigure.standard.UnauthorizedException;
import com.sentio.user_service.auth.dto.*;
import com.sentio.user_service.auth.oauth.GoogleAccountResolver;
import com.sentio.user_service.auth.oauth.dto.GoogleIdentity;
import com.sentio.user_service.auth.token.TokenIssuer;
import com.sentio.user_service.organization.OrganizationMemberRepository;
import com.sentio.user_service.organization.OrganizationProvisioningService;
import com.sentio.user_service.organization.entity.OrganizationMember;
import com.sentio.user_service.organization.enums.OrgRole;
import com.sentio.user_service.organization.enums.PlanTier;
import com.sentio.user_service.refresh_token.RefreshToken;
import com.sentio.user_service.refresh_token.RefreshTokenRepository;
import com.sentio.user_service.user.dto.UserContextResponse;
import com.sentio.user_service.user.entity.User;
import com.sentio.user_service.user.entity.UserIdentity;
import com.sentio.user_service.user.enums.AuthProvider;
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
 * {@link GoogleAccountResolver} (who is this Google user) and {@link
 * TokenIssuer} (JWT + refresh token issuance) - this class only wires them
 * together and decides which one to call.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OpaqueTokenService opaqueTokenService;
    private final JwtBlacklistService jwtBlacklistService;

    private final TokenIssuer tokenIssuer;
    private final OrganizationProvisioningService organizationProvisioning;
    private final GoogleAccountResolver googleAccountResolver;

    @Transactional
    public AuthResult register(RegistrationRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResourceAlreadyExistsException("User with email: " + request.email() + " already exists");
        }

        User user = buildLocalUser(request);
        userRepository.save(user);
        user.getIdentities().add(localIdentity(user));

        OrganizationMember membership = request.orgRole() == OrgRole.OWNER
                ? createOwnedOrganization(user, request)
                : organizationProvisioning.joinExistingOrganization(user, request.slug(), request.orgRole());

        return new AuthResult(tokenIssuer.issue(user, membership), toUserContext(user, membership));
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        OrganizationMember membership = defaultMembership(user);
        return new AuthResult(tokenIssuer.issue(user, membership), toUserContext(user, membership));
    }

    @Transactional
    public AuthResult loginOrRegisterWithGoogle(GoogleIdentity identity) {
        User user = googleAccountResolver.resolveOrCreate(identity);

        // A user that already had a default membership (existing identity, or an
        // account we just linked) keeps it. A brand new user has none yet - give
        // them ownership of a brand new org, same as local OWNER registration.
        OrganizationMember membership = organizationMemberRepository.findByUserIdAndIsDefaultTrue(user.getId())
                .orElseGet(() -> organizationProvisioning.createOwnerMembership(
                        user, defaultOrgName(identity), null, PlanTier.SOLO));

        return new AuthResult(tokenIssuer.issue(user, membership), toUserContext(user, membership));
    }

    // Google doesn't always return family_name (and in principle could omit given_name
    // too) - naive concatenation would silently produce names like "John null".
    private String defaultOrgName(GoogleIdentity identity) {
        String firstName = identity.firstName() != null ? identity.firstName() : "";
        String lastName = identity.lastName() != null ? identity.lastName() : "";
        String name = (firstName + " " + lastName).trim();
        return name.isBlank() ? identity.email() : name;
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
        OrganizationMember membership = defaultMembership(user);

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
            jwtBlacklistService.addToBlacklist(accessToken, jwtService.extractExpiration(accessToken).toEpochMilli());
        }
    }

    private OrganizationMember createOwnedOrganization(User user, RegistrationRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Organization name is required to create a new organization");
        }
        return organizationProvisioning.createOwnerMembership(user, request.name(), request.edrpou(), request.plan());
    }

    private OrganizationMember defaultMembership(User user) {
        return organizationMemberRepository.findByUserIdAndIsDefaultTrue(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("OrganizationMember", "userId", user.getId()));
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

    private UserIdentity localIdentity(User user) {
        return UserIdentity.builder()
                .user(user)
                .provider(AuthProvider.LOCAL)
                .providerUserId(user.getId().toString())
                .build();
    }

    private UserContextResponse toUserContext(User user, OrganizationMember membership) {
        return UserContextResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .lastName(user.getLastName())
                .firstName(user.getFirstName())
                .orgRole(membership.getRole().name())
                .orgName(membership.getOrganization().getName())
                .build();
    }
}

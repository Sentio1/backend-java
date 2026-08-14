package com.sentio.user_service.auth;

import com.lisovskyi.security.autoconfigure.security.jwt.JwtBlacklistService;
import com.lisovskyi.security.autoconfigure.security.jwt.JwtProperties;
import com.lisovskyi.security.autoconfigure.security.jwt.JwtService;
import com.lisovskyi.security.autoconfigure.security.jwt.OpaqueTokenService;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceAlreadyExistsException;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.lisovskyi.web.error.autoconfigure.standard.UnauthorizedException;
import com.sentio.user_service.auth.dto.*;
import com.sentio.user_service.organization.OrganizationMemberRepository;
import com.sentio.user_service.organization.OrganizationRepository;
import com.sentio.user_service.organization.entity.Organization;
import com.sentio.user_service.organization.entity.OrganizationMember;
import com.sentio.user_service.organization.enums.OrgRole;
import com.sentio.user_service.organization.enums.PlanTier;
import com.sentio.user_service.organization.enums.SubscriptionStatus;
import com.sentio.user_service.refresh_token.RefreshToken;
import com.sentio.user_service.refresh_token.RefreshTokenRepository;
import com.sentio.user_service.security.SecurityUser;
import com.sentio.user_service.user.UserRepository;
import com.sentio.user_service.user.entity.User;
import com.sentio.user_service.user.entity.UserIdentity;
import com.sentio.user_service.user.enums.AuthProvider;
import com.sentio.user_service.user.enums.PlatformRole;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OpaqueTokenService opaqueTokenService;
    private final JwtProperties jwtProperties;
    private final JwtBlacklistService jwtBlacklistService;

    @Transactional
    public AuthResult register(RegistrationRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResourceAlreadyExistsException("User with email: " + request.email() + " already exists");
        }

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

        userRepository.save(user);

        UserIdentity userIdentity = UserIdentity.builder()
                .user(user)
                .provider(AuthProvider.LOCAL)
                .providerUserId(user.getId().toString())
                .build();

        user.getIdentities().add(userIdentity);


        Organization organization = switch (request.orgRole()) {
            case OrgRole.OWNER ->
                createNewOrganization(request);
            case OrgRole.LAWYER, OrgRole.ASSISTANT -> {
                if (request.slug() == null || request.slug().isBlank()) {
                    throw new IllegalArgumentException("Organization slug is required to create a new organization");
                }
                yield findExistedOrganization(request.slug());
            }
        };

        OrganizationMember organizationMember = OrganizationMember.builder()
                .user(user)
                .organization(organization)
                .role(request.orgRole())
                .isDefault(true)
                .build();

        organizationMemberRepository.save(organizationMember);

        UserContextResponse userContextResponse = UserContextResponse.builder()
                .id(user.getId())
                .email(request.email())
                .lastName(request.lastName())
                .firstName(request.firstName())
                .orgRole(request.orgRole().name())
                .orgName(organization.getName())
                .build();

        return new AuthResult(generateAuthResponse(user, organizationMember), userContextResponse);
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        OrganizationMember organizationMember = organizationMemberRepository.findByUserIdAndIsDefaultTrue(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("OrganizationMember", "userId", user.getId()));

        UserContextResponse userContext = UserContextResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .lastName(user.getLastName())
                .firstName(user.getFirstName())
                .orgRole(organizationMember.getRole().toString())
                .orgName(organizationMember.getOrganization().getName())
                .build();

        return new AuthResult(generateAuthResponse(user, organizationMember), userContext);
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


        long userId = refreshToken.getUser().getId();
        OrganizationMember organizationMember = organizationMemberRepository.findByUserIdAndIsDefaultTrue(userId)
                .orElseThrow(() -> new ResourceNotFoundException("OrganizationMember", "userId", userId));

        AuthTokens authTokens = generateAuthResponse(refreshToken.getUser(), organizationMember);
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

    private Organization createNewOrganization(RegistrationRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Organization name is required to create a new organization");
        }

        Organization organization = Organization.builder()
                .name(request.name())
                .slug(UUID.randomUUID().toString())
                .edrpou(request.edrpou())
                .plan(request.plan() != null ? request.plan() : PlanTier.SOLO)
                .subscriptionStatus(SubscriptionStatus.TRIALING)
                .trialEndsAt(Instant.now().plus(14, ChronoUnit.DAYS))
                .build();

        return organizationRepository.save(organization);
    }

    private Organization findExistedOrganization(String slug) {
        return organizationRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "slug", slug));
    }

    private AuthTokens generateAuthResponse(User user, OrganizationMember membership) {
        SecurityUser securityUser = new SecurityUser(user);

        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("org_id", membership.getOrganization().getId());

        List<String> roles = new ArrayList<>();
        roles.add(membership.getRole().name()); // OWNER, LAWYER, ASSISTANT
        if (user.getPlatformRole() == PlatformRole.ADMIN) {
            roles.add("ADMIN");
        }
        extraClaims.put("roles", roles);

        String accessToken = jwtService.generateToken(securityUser, extraClaims);
        String refreshToken = opaqueTokenService.generate();
        String hashedRefreshToken = opaqueTokenService.hash(refreshToken);

        RefreshToken refreshTokenInstance = RefreshToken.builder()
                        .tokenHash(hashedRefreshToken)
                        .user(user)
                        .expiresAt(Instant.now().plus(jwtProperties.getRefreshTokenExpiration(), ChronoUnit.MILLIS))
                        .build();

        refreshTokenRepository.save(refreshTokenInstance);

        return new AuthTokens(accessToken, refreshToken);
    }
}

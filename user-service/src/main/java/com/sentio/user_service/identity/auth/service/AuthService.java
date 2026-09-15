package com.sentio.user_service.identity.auth.service;

import static com.sentio.shared.persistence.ConstraintViolations.isUniqueConstraintViolation;

import com.lisovskyi.security.autoconfigure.security.jwt.JwtBlacklistService;
import com.lisovskyi.security.autoconfigure.security.jwt.JwtService;
import com.lisovskyi.security.autoconfigure.security.jwt.OpaqueTokenService;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceAlreadyExistsException;
import com.lisovskyi.web.error.autoconfigure.standard.UnauthorizedException;
import com.sentio.shared.entity.id.user.UserId;
import com.sentio.user_service.identity.auth.dto.request.LoginRequest;
import com.sentio.user_service.identity.auth.dto.request.RegistrationRequest;
import com.sentio.user_service.identity.auth.dto.response.AuthResult;
import com.sentio.user_service.identity.auth.dto.response.AuthTokens;
import com.sentio.user_service.identity.auth.oauth.GoogleAccountResolver;
import com.sentio.user_service.identity.auth.oauth.dto.GoogleIdentity;
import com.sentio.user_service.identity.auth.token.TokenIssuer;
import com.sentio.user_service.identity.organization.api.dto.OrganizationMemberDto;
import com.sentio.user_service.identity.organization.internal.OrganizationConstants;
import com.sentio.user_service.identity.organization.api.enums.PlanTier;
import com.sentio.user_service.identity.organization.internal.service.OrganizationCreationService;
import com.sentio.user_service.identity.organization.internal.service.OrganizationServiceImpl;
import com.sentio.user_service.refresh_token.api.dto.RefreshTokenDto;
import com.sentio.user_service.refresh_token.api.service.RefreshTokenService;
import com.sentio.user_service.identity.user.api.dto.UserDto;
import com.sentio.user_service.identity.user.api.service.UserService;
import com.sentio.user_service.identity.user.internal.entity.User;
import com.sentio.user_service.identity.user.internal.mapper.UserMapper;
import com.sentio.user_service.identity.user.internal.repository.UserRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the auth use cases (local register/login, Google sign-in, refresh, logout).
 * Delegates the actual mechanics to focused collaborators: {@link OrganizationCreationService}
 * (creating/joining an organization), {@link OrganizationServiceImpl} (looking up an existing
 * membership - it owns organization/membership state, this class never touches it directly), {@link
 * GoogleAccountResolver} (who is this Google user) and {@link TokenIssuer} (JWT + refresh token
 * issuance) - this class only wires them together and decides which one to call.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    public static final String INVALID_ERROR_MSG = "Invalid email or password";

    private final UserRepository userRepository;
    private final UserService userService;
    private final UserMapper userMapper;

    private final JwtService jwtService;
    private final OpaqueTokenService opaqueTokenService;
    private final JwtBlacklistService jwtBlacklistService;
    private final OrganizationServiceImpl organizationService;
    private final RefreshTokenService refreshTokenService;
    private final AuthGuards authGuards;

    private final TokenIssuer tokenIssuer;
    private final PasswordEncoder passwordEncoder;
    private final OrganizationCreationService organizationProvisioning;
    private final GoogleAccountResolver googleAccountResolver;

    @Transactional
    public AuthResult register(RegistrationRequest request, String ip, String userAgent) {
        log.debug("Attempting to register user with email: {}", request.email());
        if (userRepository.existsByEmail(request.email())) {
            log.warn("Registration failed: user with email {} already exists", request.email());
            throw new ResourceAlreadyExistsException("User with email: " + request.email() + " already exists");
        }
        // phoneNumber - опційне поле (RegistrationRequest не має @NotBlank на ньому), а колонка
        // nullable/UNIQUE - existsByPhoneNumber(null) не варто викликати: похідний запит зрівняв
        // би з IS NULL і повернув би true, щойно з'явився б хоч один інший юзер без телефону.
        if (request.phoneNumber() != null
                && !request.phoneNumber().isBlank()
                && userRepository.existsByPhoneNumber(request.phoneNumber())) {
            log.warn("Registration failed: user with phone number {} already exists", request.phoneNumber());
            throw new ResourceAlreadyExistsException(
                    "User with phone number: " + request.phoneNumber() + " already exists");
        }

        User user = buildLocalUser(request);

        try {
            User savedUser = userRepository.saveAndFlush(user);

            // Add the local identity now that the user has an ID, and save again
            savedUser.getIdentities().add(userMapper.toLocalIdentity(savedUser));
            savedUser = userRepository.saveAndFlush(savedUser);

            log.info("Successfully registered user with email: {}", request.email());
            UserDto userDto = userService.findUserById(savedUser.getId());
            return new AuthResult(
                    tokenIssuer.issue(userDto, null, ip, userAgent),
                    userService.buildUserContext(savedUser.getId(), null));
        } catch (DataIntegrityViolationException e) {
            // Real index name from V6__make_users_email_unique.sql - a plain
            // "uq_users_email" here would never match, so this catch would always
            // fall through to `throw e` and leak the raw 500 this was meant to avoid.
            if (isUniqueConstraintViolation(e, "users_email_active_idx")) {
                log.warn(
                        "Registration failed (constraint violation): user with email {} already exists",
                        request.email());
                throw new ResourceAlreadyExistsException("User with email: " + request.email() + " already exists");
            }
            // Race-safety net for the phoneNumber pre-check above, same reasoning as the email
            // branch: default Postgres naming for a column-level UNIQUE constraint ("users_phone_
            // number_key" here, see V3__auth_schema.sql), so this is the one actually thrown.
            if (isUniqueConstraintViolation(e, "users_phone_number_key")) {
                log.warn(
                        "Registration failed (constraint violation): user with phone number {} already exists",
                        request.phoneNumber());
                throw new ResourceAlreadyExistsException(
                        "User with phone number: " + request.phoneNumber() + " already exists");
            }
            log.error("DataIntegrityViolationException during registration for email: {}", request.email(), e);
            throw e;
        }
    }

    @Transactional
    public AuthResult login(LoginRequest request, String ip, String userAgent) {
        log.debug("Attempting to login user with email: {}", request.email());
        User user = userRepository.findByEmail(request.email()).orElseThrow(() -> {
            log.warn("Login failed: user with email {} not found", request.email());
            return new UnauthorizedException(INVALID_ERROR_MSG);
        });

        authGuards.assertNotDeleted(user, INVALID_ERROR_MSG);

        if (user.getPassword() != null && !passwordEncoder.matches(request.password(), user.getPassword())) {
            log.warn("Login failed: invalid password for user with email {}", request.email());
            throw new UnauthorizedException(INVALID_ERROR_MSG);
        }

        OrganizationMemberDto membership =
                organizationService.findDefaultMembership(user.getId()).orElse(null);

        UserDto userDto = userService.findUserById(user.getId());

        log.info("Successfully logged in user with email: {}", request.email());

        return new AuthResult(
                tokenIssuer.issue(userDto, membership, ip, userAgent),
                userService.buildUserContext(user.getId(), membership));
    }

    @Transactional
    public AuthResult loginOrRegisterWithGoogle(GoogleIdentity identity, String ip, String userAgent) {
        log.debug("Attempting to login/register with Google for email: {}", identity.email());
        User user = googleAccountResolver.resolveOrCreate(identity);

        authGuards.assertNotDeleted(user);

        // A user that already had a default membership (existing identity, or an
        // account we just linked) keeps it. A brand new user has none yet - give
        // them ownership of a brand new org, same as local OWNER registration.
        OrganizationMemberDto membership = organizationService
                .findDefaultMembership(user.getId())
                .orElseGet(() -> {
                    log.info("Creating default organization for new Google user: {}", identity.email());
                    return organizationProvisioning.createOwnerMembership(
                            UserId.of(user.getId()), defaultOrgName(identity), null, PlanTier.SOLO);
                });

        UserDto userDto = userService.findUserById(user.getId());

        log.info("Successfully logged in/registered Google user with email: {}", identity.email());

        return new AuthResult(
                tokenIssuer.issue(userDto, membership, ip, userAgent),
                userService.buildUserContext(user.getId(), membership));
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
        log.debug("Attempting to refresh tokens");
        String hashedToken = opaqueTokenService.hash(rawToken);

        RefreshTokenDto refreshToken = refreshTokenService.findByTokenHash(hashedToken)
                .orElseThrow(() -> {
                    log.warn("Refresh failed: token not found");
                    return new UnauthorizedException("Invalid refresh token");
                });

        if (refreshToken.revokedAt() != null) {
            log.warn(
                    "Refresh failed: token has been revoked (userId: {})",
                    refreshToken.userId());
            throw new UnauthorizedException("Refresh token has been revoked");
        }
        if (refreshToken.expiresAt().isBefore(Instant.now())) {
            log.warn(
                    "Refresh failed: token has expired (userId: {})",
                    refreshToken.userId());
            throw new UnauthorizedException("Refresh token has expired");
        }

        User user = userRepository
                .findById(refreshToken.userId())
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        authGuards.assertNotDeleted(user);

        OrganizationMemberDto membership =
                organizationService.findDefaultMembership(user.getId()).orElse(null);
        UserDto userDto = userService.findUserById(user.getId());

        AuthTokens authTokens = tokenIssuer.issue(userDto, membership, ip, userAgent);
        // Revoke just the token being rotated, not every active session for this
        // user - revokeAllActiveForUser(userId) takes a userId, and passing the
        // refresh token's own id there would silently revoke a different user's
        // sessions (or nobody's, if no user happens to share that id).
        refreshTokenService.revokeSession(refreshToken.userId(), refreshToken.id());

        log.info("Successfully refreshed tokens for userId: {}", user.getId());
        return authTokens;
    }

    @Transactional
    public void logout(String accessToken, String refreshToken) {
        log.debug("Attempting to logout user");
        if (refreshToken != null) {
            String hashedToken = opaqueTokenService.hash(refreshToken);

            refreshTokenService.findByTokenHash(hashedToken)
                .ifPresentOrElse(
                        rt -> {
                            refreshTokenService.revokeSession(rt.userId(), rt.id());
                            log.info(
                                    "Successfully revoked refresh token on logout for userId: {}",
                                    rt.userId());
                        },
                        () -> log.debug("Refresh token not found during logout")
                );
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

package com.sentio.user_service.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lisovskyi.security.autoconfigure.security.jwt.JwtBlacklistService;
import com.lisovskyi.security.autoconfigure.security.jwt.JwtProperties;
import com.lisovskyi.security.autoconfigure.security.jwt.JwtService;
import com.lisovskyi.security.autoconfigure.security.jwt.OpaqueTokenService;
import com.lisovskyi.web.error.autoconfigure.standard.UnauthorizedException;
import com.sentio.user_service.identity.auth.dto.request.LoginRequest;
import com.sentio.user_service.identity.auth.dto.request.RegistrationRequest;
import com.sentio.user_service.identity.auth.dto.response.AuthResult;
import com.sentio.user_service.identity.auth.dto.response.AuthTokens;
import com.sentio.user_service.identity.auth.exception.RefreshTokenReusedException;
import com.sentio.user_service.identity.auth.oauth.GoogleAccountResolver;
import com.sentio.user_service.identity.auth.oauth.dto.GoogleIdentity;
import com.sentio.user_service.identity.auth.service.AuthGuards;
import com.sentio.user_service.identity.auth.service.AuthService;
import com.sentio.user_service.identity.auth.service.PasswordVerifier;
import com.sentio.user_service.identity.auth.token.TokenIssuer;
import com.sentio.user_service.identity.organization.api.dto.OrganizationMemberDto;
import com.sentio.user_service.identity.organization.api.enums.OrgRole;
import com.sentio.user_service.identity.organization.api.service.OrganizationMemberService;
import com.sentio.user_service.identity.user.api.SecurityUser;
import com.sentio.user_service.identity.user.api.dto.NewLocalUser;
import com.sentio.user_service.identity.user.api.dto.UserContextResponse;
import com.sentio.user_service.identity.user.api.dto.UserDto;
import com.sentio.user_service.identity.user.api.enums.PlatformRole;
import com.sentio.user_service.identity.user.api.service.UserAccountService;
import com.sentio.user_service.identity.user.api.service.UserService;
import com.sentio.user_service.refresh_token.api.dto.RefreshTokenDto;
import com.sentio.user_service.refresh_token.api.enums.RevokeReason;
import com.sentio.user_service.refresh_token.api.service.RefreshTokenService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit tests for {@link AuthService} - a thin orchestrator over the user/organization/refresh_token
 * module APIs, {@link GoogleAccountResolver} and {@link TokenIssuer}, all mocked here and covered by
 * their own tests. What's left at this level is the decision logic: who gets rejected, which
 * membership goes into the token, how a presented refresh token is judged.
 *
 * <p>passwordEncoder/PasswordVerifier/AuthGuards/jwtService/opaqueTokenService stay real (pure
 * logic, no I/O).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    // Test-only RSA private key (PKCS8 DER, base64) - never the real Doppler key.
    private static final String DUMMY_PRIVATE_KEY =
            "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDZrWu2iGsRL6OUKzPyqj/TMbtEEIEHrrJgEOsYqNawp/1UctJ1jtvkJ6oK5lOAMpIPNPc9p87XrwNUng/adBH1eG1RFR/FK0vgCgETxY9TFFGuaMR4qZhOxVHWfFvqrREuZc0/Pen0LJZak6usJyc3SAyrxij6IOsiNEDxvQwVSadcp36L5FUJ93RpepTJl1c4ktFmlhU8wouM6vgflHHRVUe9OySmL8ODw1iJcnPydZ1ewq4iutlBRp7puH3Isb7vW8kVu3qOW7H1n/XKLkMuKjENaSwBIz3+eCMtBIa9xaCW6DEVEURhtulAxuGUOrlEUaPAJVitEM/cgFxh6DXhAgMBAAECggEAEMz6CI1YaCvrXfMEsCjUQsZum/cDInbpFRGRN6bGZT2eB5/SHxku3xnxnaQ/0x/0FoDhyUwzooGDSgtmEVgOj8ni9BRjjpoEIe9bvG0t3f1uPX4gFekPFJtVsO6JwJ/peNGKKSSr8zjixOxrXl7qP7HLqpFhlcanJ01tqsrKzTSs6cOxmpJuqF0KxJgTOY+uVhiULWxu0f8c9jHPFrRQ8OgnXrX++vSAfv9keEL7gbIKxfKunGUen3nXHi3FMJ5I82e9TSjAYDohbcZOZYSVEGrk4FXlC8j6XohR/4outKdKmAfbEQvDPZ2oT8T56d/T2/mznS4eBqq1+g7XAMKfzQKBgQDs9vN7O+vJb59c7pnWxOEVXgrpBn9KOXuPkKwzDVvGMTKTyeyAe8S5QyY8AFGsS30Z2MW4IRYxjgTwxjpGRVkLUiKxvl/qXUUoNYbBFf4WKLfsRQ1AK3DIdzzw2alHkcdWQ/YDlW8UDBHs6YdkDoUwbf/SX9R/r/+Vwht2Ty3yJQKBgQDrKdacn4QURiGG7gmBZJXfLLa72cntxecknCKQw8E4+BQ236ocIKA76fL2A2r2DY44i2sZTaK7ITjeiKs6kWeg0L9WYbExuoHwJxQmGVjGHiI548nxCdzP6vmHLlakNg1ikA+DoTjhH27phHdrDpxU1ROZvHC4wroRkC+6u6IiDQKBgC6xktTrv9CXsD1tvt61OO0u9NNqNlb38MMfbO86aKUrOJ4qofHHccJX2wbjwTREQ8h+EKfxzR/CrnKLfRwvuhYi/zcrHldePaxor78IiGLxbxydlrjYVocKB/YlzdeOgEsdZTLblWHL5xRaCBXNTq12X3yi6YqnsaNe9m5ft9wJAoGBAKr+oyUEAKBVVm+sipDhuPCsrLrvZBtW+fnu5ltpXAi2qswz2pfVSW4HcTldxtrfhHitN9UQVLHJOHbn3coajMWsxFRleNj2CyG66LXDXH/CzZRWhDKWv08YRxT6ptmEzDrNEdre0mMv3hBC2CqqVxaAUV5KXZSbU30N4QbhBMXJAoGAUKVTdahVmA2+qSPd7fh6eqMk2iTUCjNoeVWUQPnDhDpmiPr5tIRrFGCewRFWgkj96s1gthxGz8Hmmu+RzCYpaCHQkZCZgt34Wgf7AdWAzUCskjTADqb3FzrAWQjWGfsy6VBaADNJcsyiEhonseO27M7hPLj85vMtfTE+cdJBaEA=";

    private static final String PASSWORD = "Password123!";

    @Mock
    private UserService userService;

    @Mock
    private UserAccountService userAccountService;

    @Mock
    private OrganizationMemberService organizationMemberService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private JwtBlacklistService jwtBlacklistService;

    @Mock
    private TokenIssuer tokenIssuer;

    @Mock
    private GoogleAccountResolver googleAccountResolver;

    // Strength 4 - the minimum; nothing here is about BCrypt's cost.
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final OpaqueTokenService opaqueTokenService = new OpaqueTokenService();
    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setPrivateKey(DUMMY_PRIVATE_KEY);
        jwtProperties.setIssuer("sentio-test");
        jwtService = new JwtService(jwtProperties);

        authService = new AuthService(
                userService,
                userAccountService,
                organizationMemberService,
                refreshTokenService,
                jwtService,
                opaqueTokenService,
                jwtBlacklistService,
                new AuthGuards(),
                tokenIssuer,
                passwordEncoder,
                new PasswordVerifier(passwordEncoder),
                googleAccountResolver);
    }

    // ---- helpers -----------------------------------------------------

    private UserDto user(long id, String passwordHash, PlatformRole role) {
        return new UserDto(id, "user@sentio.dev", passwordHash, role, null, false);
    }

    private UserDto localUser(long id) {
        return user(id, passwordEncoder.encode(PASSWORD), PlatformRole.USER);
    }

    private OrganizationMemberDto membership() {
        return new OrganizationMemberDto(1L, 10L, "Acme Legal", OrgRole.OWNER);
    }

    private UserContextResponse context(long id) {
        return UserContextResponse.builder().id(id).email("user@sentio.dev").build();
    }

    private LoginRequest login(String password) {
        return new LoginRequest("user@sentio.dev", password);
    }

    private RefreshTokenDto refreshToken(
            String raw, Instant expiresAt, Instant revokedAt, RevokeReason reason) {
        return new RefreshTokenDto(
                99L, 1L, UUID.fromString("00000000-0000-0000-0000-000000000001"),
                opaqueTokenService.hash(raw), "agent", null,
                expiresAt, Instant.now().plus(30, ChronoUnit.DAYS), revokedAt, reason);
    }

    private void stubTokenIssue() {
        when(tokenIssuer.issue(any(UserDto.class), any(), any(), any()))
                .thenReturn(new AuthTokens("access-token", "refresh-token"));
    }

    // ---- register ------------------------------------------------------

    // Registration is always org-less - creating/joining an organization is a
    // separate onboarding step, never part of register() itself.
    @Nested
    class Register {

        @Test
        void registration_storesEncodedPasswordAndIssuesOrglessSession() {
            UserDto created = localUser(1L);
            when(userAccountService.registerLocal(any())).thenReturn(created);
            when(userService.buildUserContext(1L, null)).thenReturn(context(1L));
            stubTokenIssue();

            AuthResult result = authService.register(
                    new RegistrationRequest("user@sentio.dev", PASSWORD, PASSWORD, null, "Doe", "John", null),
                    "127.0.0.1", "agent");

            ArgumentCaptor<NewLocalUser> captor = ArgumentCaptor.forClass(NewLocalUser.class);
            verify(userAccountService).registerLocal(captor.capture());
            assertThat(captor.getValue().passwordHash()).isNotEqualTo(PASSWORD);
            assertThat(passwordEncoder.matches(PASSWORD, captor.getValue().passwordHash())).isTrue();

            verify(tokenIssuer).issue(eq(created), isNull(), eq("127.0.0.1"), eq("agent"));
            assertThat(result.userContext()).isEqualTo(context(1L));
            verifyNoInteractions(organizationMemberService);
        }
    }

    // ---- login ---------------------------------------------------------

    @Nested
    class Login {

        @Test
        void validCredentials_issuesSessionScopedToDefaultOrg() {
            UserDto user = localUser(1L);
            when(userAccountService.findActiveByEmail("user@sentio.dev")).thenReturn(Optional.of(user));
            when(organizationMemberService.findDefaultMembership(1L)).thenReturn(Optional.of(membership()));
            when(userService.buildUserContext(1L, membership())).thenReturn(context(1L));
            stubTokenIssue();

            AuthResult result = authService.login(login(PASSWORD), "127.0.0.1", "agent");

            verify(tokenIssuer).issue(user, membership(), "127.0.0.1", "agent");
            assertThat(result.authTokens().accessToken()).isEqualTo("access-token");
        }

        @Test
        void noDefaultOrganization_issuesOrglessSession() {
            UserDto user = localUser(1L);
            when(userAccountService.findActiveByEmail("user@sentio.dev")).thenReturn(Optional.of(user));
            when(organizationMemberService.findDefaultMembership(1L)).thenReturn(Optional.empty());
            stubTokenIssue();

            authService.login(login(PASSWORD), "127.0.0.1", "agent");

            verify(tokenIssuer).issue(eq(user), isNull(), any(), any());
        }

        @Test
        void unknownEmail_throwsGenericUnauthorized() {
            when(userAccountService.findActiveByEmail("user@sentio.dev")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(login(PASSWORD), "127.0.0.1", "agent"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage(AuthConstants.INVALID_CREDENTIALS_MSG);
            verifyNoInteractions(tokenIssuer);
        }

        @Test
        void wrongPassword_throwsSameGenericUnauthorizedAsUnknownEmail() {
            when(userAccountService.findActiveByEmail("user@sentio.dev")).thenReturn(Optional.of(localUser(1L)));

            assertThatThrownBy(() -> authService.login(login("wrong-password"), "127.0.0.1", "agent"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage(AuthConstants.INVALID_CREDENTIALS_MSG);
            verifyNoInteractions(tokenIssuer);
        }

        // Regression: a NULL password_hash (Google-only account) used to skip the
        // password check entirely - any password logged you in.
        @Test
        void accountWithoutPassword_isRejectedForAnyPassword() {
            when(userAccountService.findActiveByEmail("user@sentio.dev"))
                    .thenReturn(Optional.of(user(1L, null, PlatformRole.USER)));

            assertThatThrownBy(() -> authService.login(login("anything-at-all"), "127.0.0.1", "agent"))
                    .isInstanceOf(UnauthorizedException.class);
            verifyNoInteractions(tokenIssuer);
        }

        @Test
        void serviceAccount_isRejectedEvenWithCorrectSecret() {
            when(userAccountService.findActiveByEmail("user@sentio.dev"))
                    .thenReturn(Optional.of(user(1L, passwordEncoder.encode(PASSWORD), PlatformRole.SERVICE)));

            assertThatThrownBy(() -> authService.login(login(PASSWORD), "127.0.0.1", "agent"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage(AuthConstants.INVALID_CREDENTIALS_MSG);
            verifyNoInteractions(tokenIssuer);
        }
    }

    // ---- Google ----------------------------------------------------------

    @Nested
    class GoogleAuth {

        private final GoogleIdentity identity =
                new GoogleIdentity("google-sub-1", "user@sentio.dev", "Jane", "Doe", true);

        @Test
        void existingUser_keepsTheirDefaultMembership() {
            UserDto user = user(1L, null, PlatformRole.USER);
            when(googleAccountResolver.resolveOrCreate(identity)).thenReturn(user);
            when(organizationMemberService.findDefaultMembership(1L)).thenReturn(Optional.of(membership()));
            stubTokenIssue();

            authService.loginOrRegisterWithGoogle(identity, "127.0.0.1", "agent");

            verify(tokenIssuer).issue(user, membership(), "127.0.0.1", "agent");
        }

        // Same as local registration: no organization is created on sign-up, the
        // frontend sends an org-less user to onboarding.
        @Test
        void brandNewUser_isOrgless() {
            UserDto user = user(1L, null, PlatformRole.USER);
            when(googleAccountResolver.resolveOrCreate(identity)).thenReturn(user);
            when(organizationMemberService.findDefaultMembership(1L)).thenReturn(Optional.empty());
            stubTokenIssue();

            authService.loginOrRegisterWithGoogle(identity, "127.0.0.1", "agent");

            verify(tokenIssuer).issue(eq(user), isNull(), any(), any());
        }

        @Test
        void serviceAccount_isRejected() {
            when(googleAccountResolver.resolveOrCreate(identity)).thenReturn(user(1L, null, PlatformRole.SERVICE));

            assertThatThrownBy(() -> authService.loginOrRegisterWithGoogle(identity, "127.0.0.1", "agent"))
                    .isInstanceOf(UnauthorizedException.class);
            verifyNoInteractions(tokenIssuer);
        }
    }

    // ---- refresh ---------------------------------------------------------

    @Nested
    class Refresh {

        private static final String RAW = "raw-refresh-token";

        private void stubFound(RefreshTokenDto token) {
            when(refreshTokenService.findByTokenHash(opaqueTokenService.hash(RAW))).thenReturn(Optional.of(token));
        }

        @Test
        void validToken_rotatesWithinTheSameSession() {
            RefreshTokenDto token = refreshToken(RAW, Instant.now().plus(1, ChronoUnit.DAYS), null, null);
            UserDto user = localUser(1L);
            stubFound(token);
            when(userAccountService.findActiveById(1L)).thenReturn(Optional.of(user));
            when(organizationMemberService.findDefaultMembership(1L)).thenReturn(Optional.of(membership()));
            when(tokenIssuer.rotate(user, membership(), token, "127.0.0.1", "agent"))
                    .thenReturn(new AuthTokens("new-access", "new-refresh"));

            AuthTokens tokens = authService.refresh(RAW, "127.0.0.1", "agent");

            assertThat(tokens.refreshToken()).isEqualTo("new-refresh");
            verify(tokenIssuer, never()).issue(any(), any(), any(), any());
        }

        @Test
        void unknownToken_isRejected() {
            when(refreshTokenService.findByTokenHash(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refresh(RAW, "127.0.0.1", "agent"))
                    .isInstanceOf(UnauthorizedException.class);
            verifyNoInteractions(tokenIssuer);
        }

        @Test
        void expiredToken_isRejected() {
            stubFound(refreshToken(RAW, Instant.now().minusSeconds(1), null, null));

            assertThatThrownBy(() -> authService.refresh(RAW, "127.0.0.1", "agent"))
                    .isInstanceOf(UnauthorizedException.class);
            verifyNoInteractions(tokenIssuer);
        }

        @Test
        void loggedOutToken_isRejectedWithoutTouchingTheFamily() {
            stubFound(refreshToken(RAW, Instant.now().plus(1, ChronoUnit.DAYS),
                    Instant.now().minus(1, ChronoUnit.HOURS), RevokeReason.LOGOUT));

            assertThatThrownBy(() -> authService.refresh(RAW, "127.0.0.1", "agent"))
                    .isInstanceOf(UnauthorizedException.class)
                    .isNotInstanceOf(RefreshTokenReusedException.class);
            verify(refreshTokenService, never()).revokeFamily(any(), any());
            verifyNoInteractions(tokenIssuer);
        }

        // Two tabs refreshing with the same cookie at once - not theft.
        @Test
        void rotatedTokenWithinGracePeriod_isRejectedWithoutRevokingTheFamily() {
            stubFound(refreshToken(RAW, Instant.now().plus(1, ChronoUnit.DAYS),
                    Instant.now().minusSeconds(2), RevokeReason.ROTATED));

            assertThatThrownBy(() -> authService.refresh(RAW, "127.0.0.1", "agent"))
                    .isInstanceOf(UnauthorizedException.class)
                    .isNotInstanceOf(RefreshTokenReusedException.class);
            verify(refreshTokenService, never()).revokeFamily(any(), any());
        }

        @Test
        void rotatedTokenAfterGracePeriod_isReuse_revokesTheWholeFamily() {
            RefreshTokenDto token = refreshToken(RAW, Instant.now().plus(1, ChronoUnit.DAYS),
                    Instant.now().minus(1, ChronoUnit.MINUTES), RevokeReason.ROTATED);
            stubFound(token);

            assertThatThrownBy(() -> authService.refresh(RAW, "127.0.0.1", "agent"))
                    .isInstanceOf(RefreshTokenReusedException.class);
            verify(refreshTokenService).revokeFamily(token.familyId(), RevokeReason.REUSE_DETECTED);
            verifyNoInteractions(tokenIssuer);
        }

        @Test
        void deletedOrMissingUser_isRejected() {
            stubFound(refreshToken(RAW, Instant.now().plus(1, ChronoUnit.DAYS), null, null));
            when(userAccountService.findActiveById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refresh(RAW, "127.0.0.1", "agent"))
                    .isInstanceOf(UnauthorizedException.class);
            verifyNoInteractions(tokenIssuer);
        }

        @Test
        void serviceAccount_isRejected() {
            stubFound(refreshToken(RAW, Instant.now().plus(1, ChronoUnit.DAYS), null, null));
            when(userAccountService.findActiveById(1L))
                    .thenReturn(Optional.of(user(1L, "hash", PlatformRole.SERVICE)));

            assertThatThrownBy(() -> authService.refresh(RAW, "127.0.0.1", "agent"))
                    .isInstanceOf(UnauthorizedException.class);
            verifyNoInteractions(tokenIssuer);
        }
    }

    // ---- logout ----------------------------------------------------------

    @Nested
    class Logout {

        private String accessToken() {
            return jwtService.generateToken(
                    new SecurityUser(1L, "user@sentio.dev", null, PlatformRole.USER), Map.of());
        }

        @Test
        void bothTokensPresent_revokesTheSessionFamilyAndBlacklistsAccessToken() {
            String access = accessToken();
            RefreshTokenDto token = refreshToken("raw", Instant.now().plus(1, ChronoUnit.DAYS), null, null);
            when(refreshTokenService.findByTokenHash(opaqueTokenService.hash("raw"))).thenReturn(Optional.of(token));

            authService.logout(access, "raw");

            verify(refreshTokenService).revokeFamily(token.familyId(), RevokeReason.LOGOUT);
            verify(jwtBlacklistService).addToBlacklist(eq(access), any(Long.class));
        }

        @Test
        void onlyAccessTokenPresent_blacklistsButSkipsRefreshLookup() {
            String access = accessToken();

            authService.logout(access, null);

            verify(jwtBlacklistService).addToBlacklist(eq(access), any(Long.class));
            verifyNoInteractions(refreshTokenService);
        }

        @Test
        void onlyRefreshTokenPresent_revokesButSkipsBlacklist() {
            RefreshTokenDto token = refreshToken("raw", Instant.now().plus(1, ChronoUnit.DAYS), null, null);
            when(refreshTokenService.findByTokenHash(opaqueTokenService.hash("raw"))).thenReturn(Optional.of(token));

            authService.logout(null, "raw");

            verify(refreshTokenService).revokeFamily(token.familyId(), RevokeReason.LOGOUT);
            verifyNoInteractions(jwtBlacklistService);
        }

        @Test
        void bothTokensNull_isNoOp() {
            authService.logout(null, null);

            verifyNoInteractions(refreshTokenService, jwtBlacklistService);
        }
    }
}

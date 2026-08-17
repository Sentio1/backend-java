package com.sentio.user_service.auth;

import com.lisovskyi.security.autoconfigure.security.jwt.JwtBlacklistService;
import com.lisovskyi.security.autoconfigure.security.jwt.JwtProperties;
import com.lisovskyi.security.autoconfigure.security.jwt.JwtService;
import com.lisovskyi.security.autoconfigure.security.jwt.OpaqueTokenService;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.lisovskyi.web.error.autoconfigure.standard.UnauthorizedException;
import com.sentio.user_service.auth.dto.AuthResult;
import com.sentio.user_service.auth.dto.AuthTokens;
import com.sentio.user_service.auth.dto.LoginRequest;
import com.sentio.user_service.auth.dto.RegistrationRequest;
import com.sentio.user_service.auth.oauth.GoogleAccountResolver;
import com.sentio.user_service.auth.oauth.dto.GoogleIdentity;
import com.sentio.user_service.auth.token.TokenIssuer;
import com.sentio.user_service.organization.service.OrganizationProvisioningService;
import com.sentio.user_service.organization.service.OrganizationService;
import com.sentio.user_service.organization.entity.Organization;
import com.sentio.user_service.organization.entity.OrganizationMember;
import com.sentio.user_service.organization.enums.OrgRole;
import com.sentio.user_service.refresh_token.RefreshToken;
import com.sentio.user_service.refresh_token.RefreshTokenRepository;
import com.sentio.user_service.security.SecurityUser;
import com.sentio.user_service.user.entity.User;
import com.sentio.user_service.user.mapper.UserMapper;
import com.sentio.user_service.user.mapper.UserMapperImpl;
import com.sentio.user_service.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthService}. AuthService itself is now a thin
 * orchestrator: organization creation lives in {@link OrganizationProvisioningService},
 * Google identity resolution in {@link GoogleAccountResolver}, token issuance in
 * {@link TokenIssuer} - all mocked here and covered by their own test classes.
 * What's left to test at this level is *wiring*: does register() call the OWNER
 * path vs the join path, does login() reject bad credentials, etc.
 *
 * passwordEncoder/jwtService/opaqueTokenService stay real (pure logic, no I/O) -
 * AuthService still uses them directly for password checks and token hashing.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    // Test-only RSA private key (PKCS8 DER, base64) - never the real Doppler key.
    private static final String DUMMY_PRIVATE_KEY =
            "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDZrWu2iGsRL6OUKzPyqj/TMbtEEIEHrrJgEOsYqNawp/1UctJ1jtvkJ6oK5lOAMpIPNPc9p87XrwNUng/adBH1eG1RFR/FK0vgCgETxY9TFFGuaMR4qZhOxVHWfFvqrREuZc0/Pen0LJZak6usJyc3SAyrxij6IOsiNEDxvQwVSadcp36L5FUJ93RpepTJl1c4ktFmlhU8wouM6vgflHHRVUe9OySmL8ODw1iJcnPydZ1ewq4iutlBRp7puH3Isb7vW8kVu3qOW7H1n/XKLkMuKjENaSwBIz3+eCMtBIa9xaCW6DEVEURhtulAxuGUOrlEUaPAJVitEM/cgFxh6DXhAgMBAAECggEAEMz6CI1YaCvrXfMEsCjUQsZum/cDInbpFRGRN6bGZT2eB5/SHxku3xnxnaQ/0x/0FoDhyUwzooGDSgtmEVgOj8ni9BRjjpoEIe9bvG0t3f1uPX4gFekPFJtVsO6JwJ/peNGKKSSr8zjixOxrXl7qP7HLqpFhlcanJ01tqsrKzTSs6cOxmpJuqF0KxJgTOY+uVhiULWxu0f8c9jHPFrRQ8OgnXrX++vSAfv9keEL7gbIKxfKunGUen3nXHi3FMJ5I82e9TSjAYDohbcZOZYSVEGrk4FXlC8j6XohR/4outKdKmAfbEQvDPZ2oT8T56d/T2/mznS4eBqq1+g7XAMKfzQKBgQDs9vN7O+vJb59c7pnWxOEVXgrpBn9KOXuPkKwzDVvGMTKTyeyAe8S5QyY8AFGsS30Z2MW4IRYxjgTwxjpGRVkLUiKxvl/qXUUoNYbBFf4WKLfsRQ1AK3DIdzzw2alHkcdWQ/YDlW8UDBHs6YdkDoUwbf/SX9R/r/+Vwht2Ty3yJQKBgQDrKdacn4QURiGG7gmBZJXfLLa72cntxecknCKQw8E4+BQ236ocIKA76fL2A2r2DY44i2sZTaK7ITjeiKs6kWeg0L9WYbExuoHwJxQmGVjGHiI548nxCdzP6vmHLlakNg1ikA+DoTjhH27phHdrDpxU1ROZvHC4wroRkC+6u6IiDQKBgC6xktTrv9CXsD1tvt61OO0u9NNqNlb38MMfbO86aKUrOJ4qofHHccJX2wbjwTREQ8h+EKfxzR/CrnKLfRwvuhYi/zcrHldePaxor78IiGLxbxydlrjYVocKB/YlzdeOgEsdZTLblWHL5xRaCBXNTq12X3yi6YqnsaNe9m5ft9wJAoGBAKr+oyUEAKBVVm+sipDhuPCsrLrvZBtW+fnu5ltpXAi2qswz2pfVSW4HcTldxtrfhHitN9UQVLHJOHbn3coajMWsxFRleNj2CyG66LXDXH/CzZRWhDKWv08YRxT6ptmEzDrNEdre0mMv3hBC2CqqVxaAUV5KXZSbU30N4QbhBMXJAoGAUKVTdahVmA2+qSPd7fh6eqMk2iTUCjNoeVWUQPnDhDpmiPr5tIRrFGCewRFWgkj96s1gthxGz8Hmmu+RzCYpaCHQkZCZgt34Wgf7AdWAzUCskjTADqb3FzrAWQjWGfsy6VBaADNJcsyiEhonseO27M7hPLj85vMtfTE+cdJBaEA=";

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private JwtBlacklistService jwtBlacklistService;

    @Mock
    private TokenIssuer tokenIssuer;
    @Mock
    private OrganizationProvisioningService organizationProvisioning;
    @Mock
    private OrganizationService organizationService;
    @Mock
    private GoogleAccountResolver googleAccountResolver;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final OpaqueTokenService opaqueTokenService = new OpaqueTokenService();
    private final UserMapper userMapper = new UserMapperImpl();
    private JwtProperties jwtProperties;
    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setPrivateKey(DUMMY_PRIVATE_KEY);
        jwtProperties.setIssuer("sentio-test");
        jwtProperties.setAccessTokenExpiration(900_000L);
        jwtProperties.setRefreshTokenExpiration(604_800_000L);
        jwtService = new JwtService(jwtProperties);

        authService = new AuthService(
                userRepository,
                refreshTokenRepository,
                jwtService,
                opaqueTokenService,
                jwtBlacklistService,
                organizationService,
                tokenIssuer,
                passwordEncoder,
                organizationProvisioning,
                googleAccountResolver,
                userMapper
        );
    }

    // ---- helpers -----------------------------------------------------

    private RegistrationRequest ownerRequest(String email, String orgName) {
        return new RegistrationRequest(
                email, "password123", "password123", null,
                "Doe", "John", null,
                orgName, null, null
        );
    }

    private void stubUserSaveAssignsId(long id) {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(id);
            return u;
        });
    }

    private User persistedUser(long id, String email, String rawPassword) {
        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .firstName("Jane")
                .lastName("Doe")
                .build();
        user.setId(id);
        return user;
    }

    private OrganizationMember membership(User user, String orgName, OrgRole role) {
        Organization org = Organization.builder().name(orgName).slug("slug").build();
        org.setId(10L);
        return OrganizationMember.builder()
                .user(user)
                .organization(org)
                .role(role)
                .isDefault(true)
                .build();
    }

    private void stubTokenIssuer() {
        when(tokenIssuer.issue(any(User.class), any(OrganizationMember.class)))
                .thenReturn(new AuthTokens("access-token", "refresh-token"));
    }

    // ---- register ------------------------------------------------------

    @Nested
    class Register {

        @Test
        void ownerRegistration_delegatesOrganizationCreationAndReturnsAuthResult() {
            when(userRepository.existsByEmail("owner@sentio.dev")).thenReturn(false);
            stubUserSaveAssignsId(1L);
            stubTokenIssuer();

            when(organizationProvisioning.createOwnerMembership(any(User.class), eq("Test Firm"), any(), any()))
                    .thenAnswer(inv -> membership(inv.getArgument(0), "Test Firm", OrgRole.OWNER));

            AuthResult result = authService.register(ownerRequest("owner@sentio.dev", "Test Firm"));

            assertThat(result.userContext().id()).isEqualTo(1L);
            assertThat(result.userContext().email()).isEqualTo("owner@sentio.dev");
            assertThat(result.userContext().orgName()).isEqualTo("Test Firm");
            assertThat(result.userContext().orgRole()).isEqualTo("OWNER");
            assertThat(result.authTokens().accessToken()).isEqualTo("access-token");

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();
            assertThat(savedUser.getIdentities()).hasSize(1);
            assertThat(savedUser.getIdentities().get(0).getProviderUserId()).isEqualTo("1");
        }

        @Test
        void duplicateEmail_throwsResourceAlreadyExists() {
            when(userRepository.existsByEmail("owner@sentio.dev")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(ownerRequest("owner@sentio.dev", "Test Firm")))
                    .isInstanceOf(com.lisovskyi.web.error.autoconfigure.standard.ResourceAlreadyExistsException.class);

            verify(userRepository, never()).save(any());
        }

        // Blank-org-name validation now lives in OrganizationProvisioningService itself
        // (see OrganizationProvisioningServiceTest) - register() no longer pre-checks it,
        // it just delegates and lets that validation fire for any caller, not only this one.

        // There's no more "join an existing org by slug" path through registration -
        // non-OWNER users only ever get a membership through OrganizationInviteService's
        // accept flow (see OrganizationInviteServiceTest). A registration with no org
        // name just creates the bare user, with no membership and no org-scoped claims,
        // and waits for that invite to be accepted later.
        @Test
        void noOrgName_createsUserWithoutMembershipAndOrglessTokens() {
            when(userRepository.existsByEmail("newcomer@sentio.dev")).thenReturn(false);
            stubUserSaveAssignsId(3L);
            when(tokenIssuer.issue(any(User.class), isNull()))
                    .thenReturn(new AuthTokens("access-token", "refresh-token"));

            AuthResult result = authService.register(ownerRequest("newcomer@sentio.dev", null));

            assertThat(result.userContext().orgName()).isNull();
            assertThat(result.userContext().orgRole()).isNull();
            assertThat(result.authTokens().accessToken()).isEqualTo("access-token");
            verifyNoInteractions(organizationProvisioning);
        }
    }

    // ---- login --------------------------------------------------------

    @Nested
    class Login {

        @Test
        void validCredentials_returnsAuthResultWithDefaultOrgContext() {
            User user = persistedUser(1L, "user@sentio.dev", "password123");
            when(userRepository.findByEmail("user@sentio.dev")).thenReturn(Optional.of(user));
            when(organizationService.getDefaultMembership(1L))
                    .thenReturn(membership(user, "Acme Legal", OrgRole.LAWYER));
            stubTokenIssuer();

            AuthResult result = authService.login(new LoginRequest("user@sentio.dev", "password123"));

            assertThat(result.userContext().id()).isEqualTo(1L);
            assertThat(result.userContext().orgName()).isEqualTo("Acme Legal");
            assertThat(result.userContext().orgRole()).isEqualTo("LAWYER");
            assertThat(result.authTokens().accessToken()).isEqualTo("access-token");
        }

        @Test
        void unknownEmail_throwsUnauthorizedWithGenericMessage() {
            when(userRepository.findByEmail("ghost@sentio.dev")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@sentio.dev", "whatever123")))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Invalid email or password");
        }

        @Test
        void wrongPassword_throwsUnauthorizedWithSameGenericMessageAsUnknownEmail() {
            User user = persistedUser(1L, "user@sentio.dev", "correct-password");
            when(userRepository.findByEmail("user@sentio.dev")).thenReturn(Optional.of(user));

            // Same message as the "unknown email" case above is the point here -
            // an attacker must not be able to distinguish the two.
            assertThatThrownBy(() -> authService.login(new LoginRequest("user@sentio.dev", "wrong-password")))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Invalid email or password");
        }

        @Test
        void noDefaultOrganization_throwsResourceNotFoundException() {
            User user = persistedUser(1L, "user@sentio.dev", "password123");
            when(userRepository.findByEmail("user@sentio.dev")).thenReturn(Optional.of(user));
            when(organizationService.getDefaultMembership(1L))
                    .thenThrow(new ResourceNotFoundException("OrganizationMember", "userId", 1L));

            assertThatThrownBy(() -> authService.login(new LoginRequest("user@sentio.dev", "password123")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ---- loginOrRegisterWithGoogle --------------------------------------

    @Nested
    class GoogleAuth {

        private GoogleIdentity identity() {
            return new GoogleIdentity("google-sub-1", "user@sentio.dev", "Jane", "Doe", true);
        }

        @Test
        void existingUser_reusesTheirDefaultMembershipAndDoesNotCreateAnOrganization() {
            User user = persistedUser(1L, "user@sentio.dev", "unused");
            when(googleAccountResolver.resolveOrCreate(identity())).thenReturn(user);
            when(organizationService.findDefaultMembership(1L))
                    .thenReturn(Optional.of(membership(user, "Acme Legal", OrgRole.OWNER)));
            stubTokenIssuer();

            AuthResult result = authService.loginOrRegisterWithGoogle(identity());

            assertThat(result.userContext().orgName()).isEqualTo("Acme Legal");
            verifyNoInteractions(organizationProvisioning);
        }

        @Test
        void brandNewUser_createsOwnerMembershipNamedAfterTheGoogleProfile() {
            User user = persistedUser(5L, "user@sentio.dev", "unused");
            when(googleAccountResolver.resolveOrCreate(identity())).thenReturn(user);
            when(organizationService.findDefaultMembership(5L)).thenReturn(Optional.empty());
            when(organizationProvisioning.createOwnerMembership(eq(user), eq("Jane Doe"), eq(null), any()))
                    .thenReturn(membership(user, "Jane Doe", OrgRole.OWNER));
            stubTokenIssuer();

            AuthResult result = authService.loginOrRegisterWithGoogle(identity());

            assertThat(result.userContext().orgName()).isEqualTo("Jane Doe");
            assertThat(result.userContext().orgRole()).isEqualTo("OWNER");
        }

        @Test
        void brandNewUser_withMissingFamilyName_doesNotProduceLiteralNullInOrgName() {
            GoogleIdentity noLastName = new GoogleIdentity("google-sub-2", "user@sentio.dev", "Jane", null, true);
            User user = persistedUser(6L, "user@sentio.dev", "unused");
            when(googleAccountResolver.resolveOrCreate(noLastName)).thenReturn(user);
            when(organizationService.findDefaultMembership(6L)).thenReturn(Optional.empty());
            when(organizationProvisioning.createOwnerMembership(eq(user), eq("Jane"), eq(null), any()))
                    .thenReturn(membership(user, "Jane", OrgRole.OWNER));
            stubTokenIssuer();

            AuthResult result = authService.loginOrRegisterWithGoogle(noLastName);

            assertThat(result.userContext().orgName()).isEqualTo("Jane");
        }

        @Test
        void brandNewUser_withNoNameAtAll_fallsBackToEmailAsOrgName() {
            GoogleIdentity noName = new GoogleIdentity("google-sub-3", "nameless@sentio.dev", null, null, true);
            User user = persistedUser(7L, "nameless@sentio.dev", "unused");
            when(googleAccountResolver.resolveOrCreate(noName)).thenReturn(user);
            when(organizationService.findDefaultMembership(7L)).thenReturn(Optional.empty());
            when(organizationProvisioning.createOwnerMembership(eq(user), eq("nameless@sentio.dev"), eq(null), any()))
                    .thenReturn(membership(user, "nameless@sentio.dev", OrgRole.OWNER));
            stubTokenIssuer();

            AuthResult result = authService.loginOrRegisterWithGoogle(noName);

            assertThat(result.userContext().orgName()).isEqualTo("nameless@sentio.dev");
        }
    }

    // ---- refresh --------------------------------------------------------

    @Nested
    class Refresh {

        @Test
        void validToken_rotatesAndReturnsNewTokens() {
            String rawToken = "raw-refresh-token";
            String hashedToken = opaqueTokenService.hash(rawToken);

            User user = persistedUser(1L, "user@sentio.dev", "password123");
            RefreshToken existing = RefreshToken.builder()
                    .user(user)
                    .tokenHash(hashedToken)
                    .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                    .build();
            when(refreshTokenRepository.findByTokenHash(hashedToken)).thenReturn(Optional.of(existing));
            when(organizationService.getDefaultMembership(1L))
                    .thenReturn(membership(user, "Acme Legal", OrgRole.LAWYER));
            stubTokenIssuer();

            AuthTokens tokens = authService.refresh(rawToken);

            assertThat(tokens.accessToken()).isEqualTo("access-token");
            assertThat(existing.getRevokedAt()).isNotNull();
            verify(refreshTokenRepository).save(existing);
        }

        @Test
        void unknownToken_throwsUnauthorizedException() {
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refresh("some-token"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Invalid refresh token");
        }

        @Test
        void revokedToken_throwsUnauthorizedExceptionAndDoesNotRotate() {
            String rawToken = "raw-refresh-token";
            String hashedToken = opaqueTokenService.hash(rawToken);
            RefreshToken revoked = RefreshToken.builder()
                    .tokenHash(hashedToken)
                    .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                    .revokedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                    .build();
            when(refreshTokenRepository.findByTokenHash(hashedToken)).thenReturn(Optional.of(revoked));

            assertThatThrownBy(() -> authService.refresh(rawToken))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Refresh token has been revoked");

            verify(refreshTokenRepository, never()).save(any());
        }

        @Test
        void expiredToken_throwsUnauthorizedExceptionAndDoesNotRotate() {
            String rawToken = "raw-refresh-token";
            String hashedToken = opaqueTokenService.hash(rawToken);
            RefreshToken expired = RefreshToken.builder()
                    .tokenHash(hashedToken)
                    .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                    .build();
            when(refreshTokenRepository.findByTokenHash(hashedToken)).thenReturn(Optional.of(expired));

            assertThatThrownBy(() -> authService.refresh(rawToken))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Refresh token has expired");

            verify(refreshTokenRepository, never()).save(any());
        }
    }

    // ---- logout ---------------------------------------------------------

    @Nested
    class Logout {

        private String issueAccessToken() {
            User user = persistedUser(1L, "user@sentio.dev", "password123");
            return jwtService.generateToken(new SecurityUser(user), Map.of());
        }

        @Test
        void bothTokensPresent_revokesRefreshAndBlacklistsAccess() {
            String rawRefreshToken = "raw-refresh-token";
            String hashedToken = opaqueTokenService.hash(rawRefreshToken);
            RefreshToken existing = RefreshToken.builder()
                    .tokenHash(hashedToken)
                    .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                    .build();
            when(refreshTokenRepository.findByTokenHash(hashedToken)).thenReturn(Optional.of(existing));
            String accessToken = issueAccessToken();

            authService.logout(accessToken, rawRefreshToken);

            assertThat(existing.getRevokedAt()).isNotNull();
            verify(refreshTokenRepository).save(existing);
            verify(jwtBlacklistService).addToBlacklist(eq(accessToken), anyLong());
        }

        @Test
        void onlyAccessTokenPresent_blacklistsButSkipsRefreshLookup() {
            String accessToken = issueAccessToken();

            authService.logout(accessToken, null);

            verify(refreshTokenRepository, never()).findByTokenHash(any());
            verify(refreshTokenRepository, never()).save(any());
            verify(jwtBlacklistService).addToBlacklist(eq(accessToken), anyLong());
        }

        @Test
        void onlyRefreshTokenPresent_revokesButSkipsBlacklist() {
            String rawRefreshToken = "raw-refresh-token";
            String hashedToken = opaqueTokenService.hash(rawRefreshToken);
            RefreshToken existing = RefreshToken.builder()
                    .tokenHash(hashedToken)
                    .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                    .build();
            when(refreshTokenRepository.findByTokenHash(hashedToken)).thenReturn(Optional.of(existing));

            authService.logout(null, rawRefreshToken);

            assertThat(existing.getRevokedAt()).isNotNull();
            verifyNoInteractions(jwtBlacklistService);
        }

        @Test
        void bothTokensNull_isNoOp() {
            authService.logout(null, null);

            verifyNoInteractions(refreshTokenRepository);
            verifyNoInteractions(jwtBlacklistService);
        }
    }
}

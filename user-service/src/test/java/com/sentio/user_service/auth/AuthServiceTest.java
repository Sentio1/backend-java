package com.sentio.user_service.auth;

import com.lisovskyi.security.autoconfigure.security.jwt.JwtBlacklistService;
import com.lisovskyi.security.autoconfigure.security.jwt.JwtProperties;
import com.lisovskyi.security.autoconfigure.security.jwt.JwtService;
import com.lisovskyi.security.autoconfigure.security.jwt.OpaqueTokenService;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceAlreadyExistsException;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.lisovskyi.web.error.autoconfigure.standard.UnauthorizedException;
import com.sentio.user_service.auth.dto.AuthResult;
import com.sentio.user_service.auth.dto.AuthTokens;
import com.sentio.user_service.auth.dto.LoginRequest;
import com.sentio.user_service.auth.dto.RegistrationRequest;
import com.sentio.user_service.organization.OrganizationMemberRepository;
import com.sentio.user_service.organization.OrganizationRepository;
import com.sentio.user_service.organization.entity.Organization;
import com.sentio.user_service.organization.entity.OrganizationMember;
import com.sentio.user_service.organization.enums.OrgRole;
import com.sentio.user_service.refresh_token.RefreshToken;
import com.sentio.user_service.refresh_token.RefreshTokenRepository;
import com.sentio.user_service.security.SecurityUser;
import com.sentio.user_service.user.UserRepository;
import com.sentio.user_service.user.entity.User;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthService}. Repositories and the blacklist are mocked
 * (I/O boundaries); JwtService/OpaqueTokenService/PasswordEncoder are real
 * instances - they're pure logic with no external dependencies, so mocking
 * them would just hide bugs in the token round-trip.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    // Test-only RSA private key (PKCS8 DER, base64) - never the real Doppler key.
    private static final String DUMMY_PRIVATE_KEY =
            "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDZrWu2iGsRL6OUKzPyqj/TMbtEEIEHrrJgEOsYqNawp/1UctJ1jtvkJ6oK5lOAMpIPNPc9p87XrwNUng/adBH1eG1RFR/FK0vgCgETxY9TFFGuaMR4qZhOxVHWfFvqrREuZc0/Pen0LJZak6usJyc3SAyrxij6IOsiNEDxvQwVSadcp36L5FUJ93RpepTJl1c4ktFmlhU8wouM6vgflHHRVUe9OySmL8ODw1iJcnPydZ1ewq4iutlBRp7puH3Isb7vW8kVu3qOW7H1n/XKLkMuKjENaSwBIz3+eCMtBIa9xaCW6DEVEURhtulAxuGUOrlEUaPAJVitEM/cgFxh6DXhAgMBAAECggEAEMz6CI1YaCvrXfMEsCjUQsZum/cDInbpFRGRN6bGZT2eB5/SHxku3xnxnaQ/0x/0FoDhyUwzooGDSgtmEVgOj8ni9BRjjpoEIe9bvG0t3f1uPX4gFekPFJtVsO6JwJ/peNGKKSSr8zjixOxrXl7qP7HLqpFhlcanJ01tqsrKzTSs6cOxmpJuqF0KxJgTOY+uVhiULWxu0f8c9jHPFrRQ8OgnXrX++vSAfv9keEL7gbIKxfKunGUen3nXHi3FMJ5I82e9TSjAYDohbcZOZYSVEGrk4FXlC8j6XohR/4outKdKmAfbEQvDPZ2oT8T56d/T2/mznS4eBqq1+g7XAMKfzQKBgQDs9vN7O+vJb59c7pnWxOEVXgrpBn9KOXuPkKwzDVvGMTKTyeyAe8S5QyY8AFGsS30Z2MW4IRYxjgTwxjpGRVkLUiKxvl/qXUUoNYbBFf4WKLfsRQ1AK3DIdzzw2alHkcdWQ/YDlW8UDBHs6YdkDoUwbf/SX9R/r/+Vwht2Ty3yJQKBgQDrKdacn4QURiGG7gmBZJXfLLa72cntxecknCKQw8E4+BQ236ocIKA76fL2A2r2DY44i2sZTaK7ITjeiKs6kWeg0L9WYbExuoHwJxQmGVjGHiI548nxCdzP6vmHLlakNg1ikA+DoTjhH27phHdrDpxU1ROZvHC4wroRkC+6u6IiDQKBgC6xktTrv9CXsD1tvt61OO0u9NNqNlb38MMfbO86aKUrOJ4qofHHccJX2wbjwTREQ8h+EKfxzR/CrnKLfRwvuhYi/zcrHldePaxor78IiGLxbxydlrjYVocKB/YlzdeOgEsdZTLblWHL5xRaCBXNTq12X3yi6YqnsaNe9m5ft9wJAoGBAKr+oyUEAKBVVm+sipDhuPCsrLrvZBtW+fnu5ltpXAi2qswz2pfVSW4HcTldxtrfhHitN9UQVLHJOHbn3coajMWsxFRleNj2CyG66LXDXH/CzZRWhDKWv08YRxT6ptmEzDrNEdre0mMv3hBC2CqqVxaAUV5KXZSbU30N4QbhBMXJAoGAUKVTdahVmA2+qSPd7fh6eqMk2iTUCjNoeVWUQPnDhDpmiPr5tIRrFGCewRFWgkj96s1gthxGz8Hmmu+RzCYpaCHQkZCZgt34Wgf7AdWAzUCskjTADqb3FzrAWQjWGfsy6VBaADNJcsyiEhonseO27M7hPLj85vMtfTE+cdJBaEA=";

    @Mock
    private UserRepository userRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private OrganizationMemberRepository organizationMemberRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private JwtBlacklistService jwtBlacklistService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final OpaqueTokenService opaqueTokenService = new OpaqueTokenService();
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
                organizationRepository,
                organizationMemberRepository,
                refreshTokenRepository,
                passwordEncoder,
                jwtService,
                opaqueTokenService,
                jwtProperties,
                jwtBlacklistService
        );
    }

    // ---- helpers -----------------------------------------------------

    private RegistrationRequest ownerRequest(String email, String orgName) {
        return new RegistrationRequest(
                email, "password123", "password123", null,
                "Doe", "John", null,
                orgName, null, null, null,
                OrgRole.OWNER
        );
    }

    private RegistrationRequest lawyerRequest(String email, String slug) {
        return new RegistrationRequest(
                email, "password123", "password123", null,
                "Doe", "Jane", null,
                null, null, slug, null,
                OrgRole.LAWYER
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

    // ---- register ------------------------------------------------------

    @Nested
    class Register {

        @Test
        void ownerRegistration_createsOrganizationAndReturnsAuthResult() {
            when(userRepository.existsByEmail("owner@sentio.dev")).thenReturn(false);
            stubUserSaveAssignsId(1L);
            when(organizationRepository.save(any(Organization.class))).thenAnswer(inv -> {
                Organization org = inv.getArgument(0);
                org.setId(10L);
                return org;
            });

            AuthResult result = authService.register(ownerRequest("owner@sentio.dev", "Test Firm"));

            assertThat(result.userContext().id()).isEqualTo(1L);
            assertThat(result.userContext().email()).isEqualTo("owner@sentio.dev");
            assertThat(result.userContext().orgName()).isEqualTo("Test Firm");
            assertThat(result.userContext().orgRole()).isEqualTo("OWNER");
            assertThat(result.authTokens().accessToken()).isNotBlank();
            assertThat(result.authTokens().refreshToken()).isNotBlank();

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();
            assertThat(savedUser.getIdentities()).hasSize(1);
            assertThat(savedUser.getIdentities().get(0).getProviderUserId()).isEqualTo("1");

            ArgumentCaptor<OrganizationMember> memberCaptor = ArgumentCaptor.forClass(OrganizationMember.class);
            verify(organizationMemberRepository).save(memberCaptor.capture());
            assertThat(memberCaptor.getValue().isDefault()).isTrue();
            assertThat(memberCaptor.getValue().getRole()).isEqualTo(OrgRole.OWNER);

            verify(refreshTokenRepository).save(any(RefreshToken.class));
        }

        @Test
        void duplicateEmail_throwsResourceAlreadyExists() {
            when(userRepository.existsByEmail("owner@sentio.dev")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(ownerRequest("owner@sentio.dev", "Test Firm")))
                    .isInstanceOf(ResourceAlreadyExistsException.class);

            verify(userRepository, never()).save(any());
        }

        @Test
        void ownerWithBlankOrgName_throwsIllegalArgumentException() {
            when(userRepository.existsByEmail("owner@sentio.dev")).thenReturn(false);
            stubUserSaveAssignsId(1L);

            assertThatThrownBy(() -> authService.register(ownerRequest("owner@sentio.dev", "  ")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Organization name is required");
        }

        @Test
        void lawyerWithBlankSlug_throwsIllegalArgumentException() {
            when(userRepository.existsByEmail("lawyer@sentio.dev")).thenReturn(false);
            stubUserSaveAssignsId(2L);

            assertThatThrownBy(() -> authService.register(lawyerRequest("lawyer@sentio.dev", " ")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Organization slug is required");
        }

        @Test
        void lawyerWithUnknownSlug_throwsResourceNotFoundException() {
            when(userRepository.existsByEmail("lawyer@sentio.dev")).thenReturn(false);
            stubUserSaveAssignsId(2L);
            when(organizationRepository.findBySlug("unknown-slug")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.register(lawyerRequest("lawyer@sentio.dev", "unknown-slug")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void lawyerJoiningExistingOrg_usesOrganizationNameNotRequestName() {
            when(userRepository.existsByEmail("lawyer@sentio.dev")).thenReturn(false);
            stubUserSaveAssignsId(2L);

            Organization existingOrg = Organization.builder()
                    .name("Acme Legal")
                    .slug("acme")
                    .build();
            existingOrg.setId(10L);
            when(organizationRepository.findBySlug("acme")).thenReturn(Optional.of(existingOrg));

            AuthResult result = authService.register(lawyerRequest("lawyer@sentio.dev", "acme"));

            // request.name() is null for LAWYER registrations - orgName must come
            // from the organization that was actually joined, not the request.
            assertThat(result.userContext().orgName()).isEqualTo("Acme Legal");
            assertThat(result.userContext().orgRole()).isEqualTo("LAWYER");

            ArgumentCaptor<OrganizationMember> memberCaptor = ArgumentCaptor.forClass(OrganizationMember.class);
            verify(organizationMemberRepository).save(memberCaptor.capture());
            assertThat(memberCaptor.getValue().isDefault()).isTrue();
        }
    }

    // ---- login --------------------------------------------------------

    @Nested
    class Login {

        @Test
        void validCredentials_returnsAuthResultWithDefaultOrgContext() {
            User user = persistedUser(1L, "user@sentio.dev", "password123");
            when(userRepository.findByEmail("user@sentio.dev")).thenReturn(Optional.of(user));

            Organization org = Organization.builder().name("Acme Legal").slug("acme").build();
            OrganizationMember member = OrganizationMember.builder()
                    .user(user)
                    .organization(org)
                    .role(OrgRole.LAWYER)
                    .isDefault(true)
                    .build();
            when(organizationMemberRepository.findByUserIdAndIsDefaultTrue(1L)).thenReturn(Optional.of(member));

            AuthResult result = authService.login(new LoginRequest("user@sentio.dev", "password123"));

            assertThat(result.userContext().id()).isEqualTo(1L);
            assertThat(result.userContext().orgName()).isEqualTo("Acme Legal");
            assertThat(result.userContext().orgRole()).isEqualTo("LAWYER");
            assertThat(result.authTokens().accessToken()).isNotBlank();
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
            when(organizationMemberRepository.findByUserIdAndIsDefaultTrue(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(new LoginRequest("user@sentio.dev", "password123")))
                    .isInstanceOf(ResourceNotFoundException.class);
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

            Organization org = Organization.builder().name("Acme Legal").slug("acme").build();
            OrganizationMember member = OrganizationMember.builder()
                    .user(user)
                    .organization(org)
                    .role(OrgRole.LAWYER)
                    .isDefault(true)
                    .build();
            when(organizationMemberRepository.findByUserIdAndIsDefaultTrue(1L)).thenReturn(Optional.of(member));

            AuthTokens tokens = authService.refresh(rawToken);

            assertThat(tokens.accessToken()).isNotBlank();
            assertThat(tokens.refreshToken()).isNotBlank().isNotEqualTo(rawToken);
            assertThat(existing.getRevokedAt()).isNotNull();
            verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
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

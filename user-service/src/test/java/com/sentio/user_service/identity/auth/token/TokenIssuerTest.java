package com.sentio.user_service.identity.auth.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lisovskyi.security.autoconfigure.security.jwt.JwtProperties;
import com.lisovskyi.security.autoconfigure.security.jwt.JwtService;
import com.lisovskyi.security.autoconfigure.security.jwt.OpaqueTokenService;
import com.sentio.user_service.identity.auth.dto.response.AuthTokens;
import com.sentio.user_service.identity.organization.api.dto.OrganizationMemberDto;
import com.sentio.user_service.identity.organization.api.enums.OrgRole;
import com.sentio.user_service.identity.user.api.dto.UserDto;
import com.sentio.user_service.identity.user.api.enums.PlatformRole;
import com.sentio.user_service.refresh_token.api.dto.RefreshTokenDto;
import com.sentio.user_service.refresh_token.api.service.RefreshTokenService;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * jwtService/opaqueTokenService are real (pure logic, no I/O) so the token round-trip and claim
 * shaping are actually exercised, not just mocked away. Refresh token persistence and active-session
 * enforcement are owned by {@link RefreshTokenService} (mocked here, covered by its own test) - this
 * class only verifies TokenIssuer hands it the right data and JWT claims come out right.
 */
@ExtendWith(MockitoExtension.class)
class TokenIssuerTest {

    private static final String DUMMY_PRIVATE_KEY =
            "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDZrWu2iGsRL6OUKzPyqj/TMbtEEIEHrrJgEOsYqNawp/1UctJ1jtvkJ6oK5lOAMpIPNPc9p87XrwNUng/adBH1eG1RFR/FK0vgCgETxY9TFFGuaMR4qZhOxVHWfFvqrREuZc0/Pen0LJZak6usJyc3SAyrxij6IOsiNEDxvQwVSadcp36L5FUJ93RpepTJl1c4ktFmlhU8wouM6vgflHHRVUe9OySmL8ODw1iJcnPydZ1ewq4iutlBRp7puH3Isb7vW8kVu3qOW7H1n/XKLkMuKjENaSwBIz3+eCMtBIa9xaCW6DEVEURhtulAxuGUOrlEUaPAJVitEM/cgFxh6DXhAgMBAAECggEAEMz6CI1YaCvrXfMEsCjUQsZum/cDInbpFRGRN6bGZT2eB5/SHxku3xnxnaQ/0x/0FoDhyUwzooGDSgtmEVgOj8ni9BRjjpoEIe9bvG0t3f1uPX4gFekPFJtVsO6JwJ/peNGKKSSr8zjixOxrXl7qP7HLqpFhlcanJ01tqsrKzTSs6cOxmpJuqF0KxJgTOY+uVhiULWxu0f8c9jHPFrRQ8OgnXrX++vSAfv9keEL7gbIKxfKunGUen3nXHi3FMJ5I82e9TSjAYDohbcZOZYSVEGrk4FXlC8j6XohR/4outKdKmAfbEQvDPZ2oT8T56d/T2/mznS4eBqq1+g7XAMKfzQKBgQDs9vN7O+vJb59c7pnWxOEVXgrpBn9KOXuPkKwzDVvGMTKTyeyAe8S5QyY8AFGsS30Z2MW4IRYxjgTwxjpGRVkLUiKxvl/qXUUoNYbBFf4WKLfsRQ1AK3DIdzzw2alHkcdWQ/YDlW8UDBHs6YdkDoUwbf/SX9R/r/+Vwht2Ty3yJQKBgQDrKdacn4QURiGG7gmBZJXfLLa72cntxecknCKQw8E4+BQ236ocIKA76fL2A2r2DY44i2sZTaK7ITjeiKs6kWeg0L9WYbExuoHwJxQmGVjGHiI548nxCdzP6vmHLlakNg1ikA+DoTjhH27phHdrDpxU1ROZvHC4wroRkC+6u6IiDQKBgC6xktTrv9CXsD1tvt61OO0u9NNqNlb38MMfbO86aKUrOJ4qofHHccJX2wbjwTREQ8h+EKfxzR/CrnKLfRwvuhYi/zcrHldePaxor78IiGLxbxydlrjYVocKB/YlzdeOgEsdZTLblWHL5xRaCBXNTq12X3yi6YqnsaNe9m5ft9wJAoGBAKr+oyUEAKBVVm+sipDhuPCsrLrvZBtW+fnu5ltpXAi2qswz2pfVSW4HcTldxtrfhHitN9UQVLHJOHbn3coajMWsxFRleNj2CyG66LXDXH/CzZRWhDKWv08YRxT6ptmEzDrNEdre0mMv3hBC2CqqVxaAUV5KXZSbU30N4QbhBMXJAoGAUKVTdahVmA2+qSPd7fh6eqMk2iTUCjNoeVWUQPnDhDpmiPr5tIRrFGCewRFWgkj96s1gthxGz8Hmmu+RzCYpaCHQkZCZgt34Wgf7AdWAzUCskjTADqb3FzrAWQjWGfsy6VBaADNJcsyiEhonseO27M7hPLj85vMtfTE+cdJBaEA=";

    @Mock
    private RefreshTokenService refreshTokenService;

    private JwtProperties jwtProperties;
    private JwtService jwtService;
    private TokenIssuer tokenIssuer;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setPrivateKey(DUMMY_PRIVATE_KEY);
        jwtProperties.setIssuer("sentio-test");
        jwtProperties.setAccessTokenExpiration(900_000L);
        jwtProperties.setRefreshTokenExpiration(604_800_000L);
        jwtService = new JwtService(jwtProperties);

        tokenIssuer = new TokenIssuer(
                jwtProperties, jwtService, new OpaqueTokenService(), refreshTokenService, Duration.ofDays(30));
    }

    // JwtService doesn't expose a generic claims getter, and neither jjwt nor
    // Jackson leak onto this module's test classpath (both are `implementation`
    // deps elsewhere). The signature/expiry are already covered by
    // isTokenValid()/extractSubject() - here we only need to see the extra
    // claims made it into the payload, so decode the raw JSON text and check
    // for the expected fragments instead of parsing it into an object.
    private String payloadOf(String accessToken) {
        String payloadSegment = accessToken.split("\\.")[1];
        return new String(Base64.getUrlDecoder().decode(payloadSegment), StandardCharsets.UTF_8);
    }

    private UserDto user(long id, String email, PlatformRole platformRole) {
        return new UserDto(id, email, null, platformRole, null, false);
    }

    private OrganizationMemberDto membershipFor(OrgRole role) {
        return new OrganizationMemberDto(1L, 42L, "Acme Legal", role);
    }

    private RefreshTokenDto existingToken() {
        return new RefreshTokenDto(1L, 1L, UUID.randomUUID(), "irrelevant-hash", null, null,
                Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS), null, null);
    }

    private void stubRefreshTokenIssue() {
        when(refreshTokenService.startSession(any(Long.class), any(), any(), any(), any(), any()))
                .thenReturn(existingToken());
    }

    @Test
    void issue_encodesOrgIdAndRoleClaims() {
        stubRefreshTokenIssue();
        UserDto user = user(1L, "user@sentio.dev", PlatformRole.USER);
        OrganizationMemberDto membership = membershipFor(OrgRole.LAWYER);

        AuthTokens tokens = tokenIssuer.issue(user, membership, "203.0.113.5", "JUnit-Agent/1.0");

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(jwtService.extractSubject(tokens.accessToken())).isEqualTo("1");

        String payload = payloadOf(tokens.accessToken());
        assertThat(payload).contains("\"org_id\":42");
        assertThat(payload).contains("\"roles\":[\"LAWYER\"]");
    }

    @Test
    void issue_addsAdminRoleForPlatformAdmins() {
        stubRefreshTokenIssue();
        UserDto user = user(1L, "admin@sentio.dev", PlatformRole.ADMIN);
        OrganizationMemberDto membership = membershipFor(OrgRole.OWNER);

        AuthTokens tokens = tokenIssuer.issue(user, membership, "203.0.113.5", "JUnit-Agent/1.0");

        // insertion order is deterministic: membership orgRole first, then ADMIN
        assertThat(payloadOf(tokens.accessToken())).contains("\"roles\":[\"OWNER\",\"ADMIN\"]");
    }

    @Test
    void issue_persistsHashedRefreshTokenWithConfiguredExpiryAndEnforcesSessionLimitFirst() {
        stubRefreshTokenIssue();
        UserDto user = user(1L, "user@sentio.dev", PlatformRole.USER);
        OrganizationMemberDto membership = membershipFor(OrgRole.LAWYER);

        AuthTokens tokens = tokenIssuer.issue(user, membership, "203.0.113.5", "JUnit-Agent/1.0");

        verify(refreshTokenService).enforceActiveSessionLimit(1L);

        ArgumentCaptor<String> tokenHashCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Instant> expiresAtCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> familyExpiresAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(refreshTokenService)
                .startSession(eq(1L), tokenHashCaptor.capture(), eq("JUnit-Agent/1.0"), any(InetAddress.class),
                        expiresAtCaptor.capture(), familyExpiresAtCaptor.capture());

        assertThat(tokenHashCaptor.getValue()).isNotEqualTo(tokens.refreshToken()); // stored hashed, not raw
        assertThat(expiresAtCaptor.getValue())
                .isAfter(Instant.now().plusMillis(jwtProperties.getRefreshTokenExpiration() - 60_000));
        // Absolute session cap: 30 days from login, regardless of later rotations.
        assertThat(familyExpiresAtCaptor.getValue())
                .isBetween(Instant.now().plus(29, ChronoUnit.DAYS), Instant.now().plus(31, ChronoUnit.DAYS));
    }

    @Test
    void issue_malformedIp_passesNullIpInsteadOfThrowing() {
        stubRefreshTokenIssue();
        UserDto user = user(1L, "user@sentio.dev", PlatformRole.USER);
        OrganizationMemberDto membership = membershipFor(OrgRole.LAWYER);

        tokenIssuer.issue(user, membership, "not-an-ip-address", "JUnit-Agent/1.0");

        verify(refreshTokenService).startSession(eq(1L), any(), eq("JUnit-Agent/1.0"), isNull(), any(), any());
    }

    @Test
    void rotate_continuesTheSameSessionWithoutCountingAgainstTheSessionLimit() {
        RefreshTokenDto current = existingToken();
        UserDto user = user(1L, "user@sentio.dev", PlatformRole.USER);

        AuthTokens tokens = tokenIssuer.rotate(user, membershipFor(OrgRole.LAWYER), current, "203.0.113.5", "agent");

        ArgumentCaptor<String> newHashCaptor = ArgumentCaptor.forClass(String.class);
        verify(refreshTokenService).rotate(eq(current), newHashCaptor.capture(), eq("agent"), any(), any());
        assertThat(newHashCaptor.getValue()).isNotEqualTo(tokens.refreshToken());
        verify(refreshTokenService, never()).enforceActiveSessionLimit(any(Long.class));
        verify(refreshTokenService, never()).startSession(any(Long.class), any(), any(), any(), any(), any());
    }

    @Test
    void serviceAccessToken_carriesServiceRoleAndNoOrg() {
        String token = tokenIssuer.issueServiceAccessToken(user(9L, "svc@service.internal", PlatformRole.SERVICE));

        assertThat(jwtService.extractSubject(token)).isEqualTo("9");
        assertThat(payloadOf(token)).contains("\"roles\":[\"SERVICE\"]").doesNotContain("org_id");
    }

    // RFC 6749 expires_in is in seconds - JwtProperties stores millis.
    @Test
    void accessTokenTtlSeconds_convertsFromMillis() {
        assertThat(tokenIssuer.accessTokenTtlSeconds()).isEqualTo(900L);
    }
}

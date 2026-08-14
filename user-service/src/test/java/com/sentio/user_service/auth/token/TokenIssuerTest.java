package com.sentio.user_service.auth.token;

import com.lisovskyi.security.autoconfigure.security.jwt.JwtProperties;
import com.lisovskyi.security.autoconfigure.security.jwt.JwtService;
import com.lisovskyi.security.autoconfigure.security.jwt.OpaqueTokenService;
import com.sentio.user_service.auth.dto.AuthTokens;
import com.sentio.user_service.organization.entity.Organization;
import com.sentio.user_service.organization.entity.OrganizationMember;
import com.sentio.user_service.organization.enums.OrgRole;
import com.sentio.user_service.refresh_token.RefreshToken;
import com.sentio.user_service.refresh_token.RefreshTokenRepository;
import com.sentio.user_service.user.entity.User;
import com.sentio.user_service.user.enums.PlatformRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * jwtService/opaqueTokenService are real (pure logic, no I/O) so the token
 * round-trip and claim shaping are actually exercised, not just mocked away.
 * Only the refresh token persistence goes through a mock repository.
 */
@ExtendWith(MockitoExtension.class)
class TokenIssuerTest {

    private static final String DUMMY_PRIVATE_KEY =
            "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDZrWu2iGsRL6OUKzPyqj/TMbtEEIEHrrJgEOsYqNawp/1UctJ1jtvkJ6oK5lOAMpIPNPc9p87XrwNUng/adBH1eG1RFR/FK0vgCgETxY9TFFGuaMR4qZhOxVHWfFvqrREuZc0/Pen0LJZak6usJyc3SAyrxij6IOsiNEDxvQwVSadcp36L5FUJ93RpepTJl1c4ktFmlhU8wouM6vgflHHRVUe9OySmL8ODw1iJcnPydZ1ewq4iutlBRp7puH3Isb7vW8kVu3qOW7H1n/XKLkMuKjENaSwBIz3+eCMtBIa9xaCW6DEVEURhtulAxuGUOrlEUaPAJVitEM/cgFxh6DXhAgMBAAECggEAEMz6CI1YaCvrXfMEsCjUQsZum/cDInbpFRGRN6bGZT2eB5/SHxku3xnxnaQ/0x/0FoDhyUwzooGDSgtmEVgOj8ni9BRjjpoEIe9bvG0t3f1uPX4gFekPFJtVsO6JwJ/peNGKKSSr8zjixOxrXl7qP7HLqpFhlcanJ01tqsrKzTSs6cOxmpJuqF0KxJgTOY+uVhiULWxu0f8c9jHPFrRQ8OgnXrX++vSAfv9keEL7gbIKxfKunGUen3nXHi3FMJ5I82e9TSjAYDohbcZOZYSVEGrk4FXlC8j6XohR/4outKdKmAfbEQvDPZ2oT8T56d/T2/mznS4eBqq1+g7XAMKfzQKBgQDs9vN7O+vJb59c7pnWxOEVXgrpBn9KOXuPkKwzDVvGMTKTyeyAe8S5QyY8AFGsS30Z2MW4IRYxjgTwxjpGRVkLUiKxvl/qXUUoNYbBFf4WKLfsRQ1AK3DIdzzw2alHkcdWQ/YDlW8UDBHs6YdkDoUwbf/SX9R/r/+Vwht2Ty3yJQKBgQDrKdacn4QURiGG7gmBZJXfLLa72cntxecknCKQw8E4+BQ236ocIKA76fL2A2r2DY44i2sZTaK7ITjeiKs6kWeg0L9WYbExuoHwJxQmGVjGHiI548nxCdzP6vmHLlakNg1ikA+DoTjhH27phHdrDpxU1ROZvHC4wroRkC+6u6IiDQKBgC6xktTrv9CXsD1tvt61OO0u9NNqNlb38MMfbO86aKUrOJ4qofHHccJX2wbjwTREQ8h+EKfxzR/CrnKLfRwvuhYi/zcrHldePaxor78IiGLxbxydlrjYVocKB/YlzdeOgEsdZTLblWHL5xRaCBXNTq12X3yi6YqnsaNe9m5ft9wJAoGBAKr+oyUEAKBVVm+sipDhuPCsrLrvZBtW+fnu5ltpXAi2qswz2pfVSW4HcTldxtrfhHitN9UQVLHJOHbn3coajMWsxFRleNj2CyG66LXDXH/CzZRWhDKWv08YRxT6ptmEzDrNEdre0mMv3hBC2CqqVxaAUV5KXZSbU30N4QbhBMXJAoGAUKVTdahVmA2+qSPd7fh6eqMk2iTUCjNoeVWUQPnDhDpmiPr5tIRrFGCewRFWgkj96s1gthxGz8Hmmu+RzCYpaCHQkZCZgt34Wgf7AdWAzUCskjTADqb3FzrAWQjWGfsy6VBaADNJcsyiEhonseO27M7hPLj85vMtfTE+cdJBaEA=";

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

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

        tokenIssuer = new TokenIssuer(jwtService, new OpaqueTokenService(), jwtProperties, refreshTokenRepository);
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

    private OrganizationMember membershipFor(User user, OrgRole role) {
        Organization organization = Organization.builder().name("Acme Legal").slug("acme").build();
        organization.setId(42L);
        return OrganizationMember.builder()
                .user(user)
                .organization(organization)
                .role(role)
                .isDefault(true)
                .build();
    }

    @Test
    void issue_encodesOrgIdAndRoleClaims() {
        User user = User.builder().email("user@sentio.dev").build();
        user.setId(1L);
        OrganizationMember membership = membershipFor(user, OrgRole.LAWYER);

        AuthTokens tokens = tokenIssuer.issue(user, membership);

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(jwtService.extractSubject(tokens.accessToken())).isEqualTo("1");

        String payload = payloadOf(tokens.accessToken());
        assertThat(payload).contains("\"org_id\":42");
        assertThat(payload).contains("\"roles\":[\"LAWYER\"]");
    }

    @Test
    void issue_addsAdminRoleForPlatformAdmins() {
        User user = User.builder().email("admin@sentio.dev").platformRole(PlatformRole.ADMIN).build();
        user.setId(1L);
        OrganizationMember membership = membershipFor(user, OrgRole.OWNER);

        AuthTokens tokens = tokenIssuer.issue(user, membership);

        // insertion order is deterministic: membership role first, then ADMIN
        assertThat(payloadOf(tokens.accessToken())).contains("\"roles\":[\"OWNER\",\"ADMIN\"]");
    }

    @Test
    void issue_persistsHashedRefreshTokenWithConfiguredExpiry() {
        User user = User.builder().email("user@sentio.dev").build();
        user.setId(1L);
        OrganizationMember membership = membershipFor(user, OrgRole.LAWYER);

        AuthTokens tokens = tokenIssuer.issue(user, membership);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken persisted = captor.getValue();

        assertThat(persisted.getTokenHash()).isNotEqualTo(tokens.refreshToken()); // stored hashed, not raw
        assertThat(persisted.getUser()).isEqualTo(user);
        assertThat(persisted.getExpiresAt()).isAfter(Instant.now().plusMillis(jwtProperties.getRefreshTokenExpiration() - 60_000));
    }
}

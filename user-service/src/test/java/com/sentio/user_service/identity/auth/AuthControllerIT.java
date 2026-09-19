package com.sentio.user_service.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sentio.user_service.TestcontainersConfiguration;
import com.sentio.user_service.identity.auth.dto.request.LoginRequest;
import com.sentio.user_service.identity.auth.dto.request.RegistrationRequest;
import com.sentio.user_service.identity.auth.rate_limiting.RateLimitingService;
import com.sentio.user_service.identity.user.api.dto.NewExternalUser;
import com.sentio.user_service.identity.user.api.dto.UserDto;
import com.sentio.user_service.identity.user.api.enums.AuthProvider;
import com.sentio.user_service.identity.user.api.service.UserAccountService;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end tests for the /auth/** endpoints: real Spring context, real Testcontainers Postgres,
 * real security filter chain and cookies. Each test runs in its own transaction that's rolled back
 * afterwards (see {@link Transactional}), so tests don't leak state into each other despite sharing
 * one database.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // MockMvc reports the same remote address for every request in this class, and the
    // rate-limit counters live in the (shared, per test run) Redis container - so without
    // this, every /auth/register and /auth/login call here would draw from the same
    // register-by-ip / login-by-ip counter as every other IT class in the same run,
    // making pass/fail depend on execution order. This class isn't testing rate
    // limiting (RateLimitingServiceIT does), so it shouldn't be subject to it.
    @MockitoBean
    private RateLimitingService rateLimitingService;

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private String refreshCookie(MvcResult result) {
        return result.getResponse().getCookie("refresh_token").getValue();
    }

    private static RegistrationRequest registrationRequest(String email) {
        return new RegistrationRequest(email, "Password123!", "Password123!", null, "Doe", "John", null);
    }

    private MvcResult register(String email) throws Exception {
        return mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registrationRequest(email))))
                .andReturn();
    }

    // ---- register -----------------------------------------------------

    @Test
    void register_returns201WithCookiesLocationAndOrglessContext() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registrationRequest("owner1@sentio.dev"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(".*/users/\\d+$")))
                .andExpect(jsonPath("$.email").value("owner1@sentio.dev"))
                .andExpect(jsonPath("$.orgName").doesNotExist())
                .andExpect(jsonPath("$.orgRole").doesNotExist())
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().httpOnly("access_token", true))
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().httpOnly("refresh_token", true));
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        register("owner2@sentio.dev");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registrationRequest("owner2@sentio.dev"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_ALREADY_EXISTS"));
    }

    @Test
    void register_shortPassword_returns400ValidationFailed() throws Exception {
        RegistrationRequest invalid =
                new RegistrationRequest("owner3@sentio.dev", "short", "short", null, "Doe", "John", null);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ---- login ----------------------------------------------------------

    @Test
    void login_validCredentials_returns200WithFreshCookies() throws Exception {
        register("owner4@sentio.dev");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(new LoginRequest("owner4@sentio.dev", "Password123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("owner4@sentio.dev"))
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().exists("refresh_token"));
    }

    @Test
    void login_wrongPasswordAndUnknownEmail_return401WithIdenticalMessage() throws Exception {
        register("owner5@sentio.dev");

        MvcResult wrongPassword = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("owner5@sentio.dev", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        MvcResult unknownEmail = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("ghost@sentio.dev", "whatever123"))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        // Enumeration protection: the two failure modes must be indistinguishable to the
        // caller. Compare code/message only - timestamp legitimately differs per request.
        ProblemDetail wrongPasswordBody =
                objectMapper.readValue(wrongPassword.getResponse().getContentAsString(), ProblemDetail.class);
        ProblemDetail unknownEmailBody =
                objectMapper.readValue(unknownEmail.getResponse().getContentAsString(), ProblemDetail.class);

        assertThat(wrongPasswordBody.getProperties().get("code"))
                .isEqualTo(unknownEmailBody.getProperties().get("code"));
        assertThat(wrongPasswordBody.getDetail()).isEqualTo(unknownEmailBody.getDetail());
    }

    // ---- refresh --------------------------------------------------------

    @Test
    void refresh_withoutCookie_returns401() throws Exception {
        mockMvc.perform(post("/auth/refresh")).andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_validCookie_rotatesTokensAndReturns204() throws Exception {
        MvcResult registerResult = register("owner6@sentio.dev");
        MockHttpServletResponse registerResponse = registerResult.getResponse();
        String originalRefreshToken =
                registerResponse.getCookie("refresh_token").getValue();

        MvcResult refreshResult = mockMvc.perform(
                        post("/auth/refresh").cookie(new Cookie("refresh_token", originalRefreshToken)))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().exists("refresh_token"))
                .andReturn();

        // The refresh token is a SecureRandom opaque value, so it's always different on
        // rotation regardless of timing. The access (JWT) token is not compared here: its
        // iat/exp claims have second-level precision, so two tokens minted for the same
        // user within the same wall-clock second are legitimately byte-identical - that's
        // covered instead, more meaningfully, by refresh_reusingRotatedToken_returns401.
        MockHttpServletResponse refreshResponse = refreshResult.getResponse();
        assertThat(refreshResponse.getCookie("refresh_token").getValue()).isNotEqualTo(originalRefreshToken);
    }

    @Test
    void refresh_reusingRotatedToken_returns401() throws Exception {
        MvcResult registerResult = register("owner7@sentio.dev");
        String originalRefreshToken =
                registerResult.getResponse().getCookie("refresh_token").getValue();

        // First refresh: rotates and revokes the original token - succeeds.
        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", originalRefreshToken)))
                .andExpect(status().isNoContent());

        // Reusing the now-revoked original token must be rejected, not silently accepted.
        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", originalRefreshToken)))
                .andExpect(status().isUnauthorized());
    }

    // ---- logout -----------------------------------------------------------

    @Test
    void logout_clearsCookiesAndRevokesRefreshTokenServerSide() throws Exception {
        MvcResult registerResult = register("owner8@sentio.dev");
        MockHttpServletResponse registerResponse = registerResult.getResponse();
        String accessToken = registerResponse.getCookie("access_token").getValue();
        String refreshToken = registerResponse.getCookie("refresh_token").getValue();

        MvcResult logoutResult = mockMvc.perform(post("/auth/logout")
                        .cookie(new Cookie("access_token", accessToken), new Cookie("refresh_token", refreshToken)))
                .andExpect(status().isNoContent())
                .andReturn();

        Cookie clearedAccess = logoutResult.getResponse().getCookie("access_token");
        Cookie clearedRefresh = logoutResult.getResponse().getCookie("refresh_token");
        assertThat(clearedAccess.getMaxAge()).isZero();
        assertThat(clearedRefresh.getMaxAge()).isZero();

        // The refresh token must be revoked server-side too, not just forgotten client-side.
        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_withoutAnyCookies_stillReturns204() throws Exception {
        mockMvc.perform(post("/auth/logout")).andExpect(status().isNoContent());
    }

    // ---- cookies ------------------------------------------------------------

    // The refresh cookie must reach /auth/logout too (not only /auth/refresh), or a
    // browser never sends it there and logout can't revoke the session server-side.
    @Test
    void refreshCookie_isScopedToWholeAuthPath_andLivesAsLongAsTheToken() throws Exception {
        MockHttpServletResponse response = register("cookie1@sentio.dev").getResponse();

        Cookie refresh = response.getCookie("refresh_token");
        // Test config has no server.servlet.context-path - in the app this is /api/v1/auth.
        assertThat(refresh.getPath()).isEqualTo("/auth");
        assertThat(refresh.getMaxAge()).isEqualTo(604_800);
    }

    // ---- security regressions ---------------------------------------------

    // A Google-only account has no password_hash. That NULL used to skip the
    // password check entirely - any password logged you into it.
    @Test
    void login_accountWithoutPassword_returns401ForAnyPassword() throws Exception {
        String email = "google-only-" + UUID.randomUUID() + "@sentio.dev";
        userAccountService.createFromExternalIdentity(
                new NewExternalUser(AuthProvider.GOOGLE, UUID.randomUUID().toString(), email, "Jane", "Doe"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "AnyPassword123!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist("access_token"));
    }

    // 50 chars (well under PASSWORD_MAX_LENGTH) but 90 UTF-8 bytes - past what
    // BCrypt can hash - must be a 400, not a 500 from PasswordEncoder.encode().
    @Test
    void register_passwordOver72Utf8Bytes_returns400() throws Exception {
        String longCyrillic = "Password1!" + "ж".repeat(40);
        RegistrationRequest request = new RegistrationRequest(
                "cyrillic@sentio.dev", longCyrillic, longCyrillic, null, "Doe", "John", null);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ---- refresh token reuse ------------------------------------------------

    @Test
    void refresh_reusingRotatedTokenAfterGracePeriod_revokesTheWholeSession() throws Exception {
        String original = refreshCookie(register("reuse1@sentio.dev"));

        String successor = refreshCookie(mockMvc.perform(
                        post("/auth/refresh").cookie(new Cookie("refresh_token", original)))
                .andExpect(status().isNoContent())
                .andReturn());

        // Every MockMvc call here shares the test's transaction and persistence context:
        // flush the rotation to the DB first, so the UPDATE below has a row to change...
        entityManager.flush();

        // Pretend the rotation happened a minute ago - outside the grace window.
        jdbcTemplate.update(
                "UPDATE auth.refresh_tokens SET revoked_at = now() - interval '1 minute' WHERE revoke_reason = 'ROTATED'"
                        + " AND user_id = (SELECT id FROM auth.users WHERE email = 'reuse1@sentio.dev')");

        // ...then drop the cached token entities so the next request re-reads it.
        entityManager.clear();

        // An attacker replays the stolen, already-rotated token...
        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", original)))
                .andExpect(status().isUnauthorized());

        // ...which kills the legitimate holder's current token too.
        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", successor)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_reusingRotatedTokenWithinGracePeriod_keepsTheSessionAlive() throws Exception {
        String original = refreshCookie(register("reuse2@sentio.dev"));

        String successor = refreshCookie(mockMvc.perform(
                        post("/auth/refresh").cookie(new Cookie("refresh_token", original)))
                .andExpect(status().isNoContent())
                .andReturn());

        // Second tab, same cookie, same moment: rejected...
        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", original)))
                .andExpect(status().isUnauthorized());

        // ...but the session itself survives.
        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", successor)))
                .andExpect(status().isNoContent());
    }

    // ---- service token ------------------------------------------------------

    // V9 seeds the service account with a NULL hash on purpose ("not provisioned
    // here") - that must deny every secret, not accept any.
    @Test
    void serviceToken_unprovisionedServiceAccount_returns401() throws Exception {
        mockMvc.perform(post("/auth/service-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId": "registry-monitor@service.internal", "secret": "anything"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void serviceToken_validSecret_returnsBearerTokenWithExpiresInSeconds() throws Exception {
        UserDto serviceAccount = userAccountService.findActiveByEmail("registry-monitor@service.internal").orElseThrow();
        userAccountService.updatePasswordHash(serviceAccount.id(), passwordEncoder.encode("s3cret-for-tests"));

        mockMvc.perform(post("/auth/service-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId": "registry-monitor@service.internal", "secret": "s3cret-for-tests"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(900))
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(cookie().doesNotExist("refresh_token"));
    }

    @Test
    void login_serviceAccountWithCorrectSecret_returns401() throws Exception {
        UserDto serviceAccount = userAccountService.findActiveByEmail("registry-monitor@service.internal").orElseThrow();
        userAccountService.updatePasswordHash(serviceAccount.id(), passwordEncoder.encode("S3cret-for-tests!"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("registry-monitor@service.internal", "S3cret-for-tests!"))))
                .andExpect(status().isUnauthorized());
    }

    // ---- organization changes keep the session ------------------------------

    // Creating/switching an organization only re-mints the access token (new org_id
    // claim) - it used to start a whole new session every time, leaving the old one
    // active until expiry and eating into the 5-session limit.
    @Test
    void createOrganization_reissuesAccessTokenOnly_withoutStartingNewSession() throws Exception {
        MockHttpServletResponse registered = register("org-owner@sentio.dev").getResponse();
        String accessToken = registered.getCookie("access_token").getValue();
        String xsrf = registered.getCookie("XSRF-TOKEN").getValue();

        MockHttpServletResponse created = mockMvc.perform(post("/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orgName": "Acme Legal"}
                                """)
                        .cookie(new Cookie("access_token", accessToken), new Cookie("XSRF-TOKEN", xsrf))
                        .header("X-XSRF-TOKEN", xsrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orgName").value("Acme Legal"))
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().doesNotExist("refresh_token"))
                .andReturn()
                .getResponse();

        String payload = new String(java.util.Base64.getUrlDecoder()
                .decode(created.getCookie("access_token").getValue().split("\\.")[1]));
        assertThat(payload).contains("\"org_id\"").contains("OWNER");

        entityManager.flush();
        Integer activeSessions = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM auth.refresh_tokens rt JOIN auth.users u ON u.id = rt.user_id"
                        + " WHERE u.email = 'org-owner@sentio.dev' AND rt.revoked_at IS NULL",
                Integer.class);
        assertThat(activeSessions).isEqualTo(1);
    }
}

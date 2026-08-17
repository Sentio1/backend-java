package com.sentio.user_service.auth;

import tools.jackson.databind.ObjectMapper;
import com.lisovskyi.web.error.autoconfigure.ErrorResponse;
import com.sentio.user_service.TestcontainersConfiguration;
import com.sentio.user_service.auth.dto.LoginRequest;
import com.sentio.user_service.auth.dto.RegistrationRequest;
import com.sentio.user_service.auth.rate_limiting.RateLimitingService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for the /auth/** endpoints: real Spring context, real
 * Testcontainers Postgres, real security filter chain and cookies. Each test
 * runs in its own transaction that's rolled back afterwards (see
 * {@link Transactional}), so tests don't leak state into each other despite
 * sharing one database.
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
    // RateLimiterRegistry lives in the (context-cached) Spring context, not per test -
    // so without this, every /auth/register and /auth/login call here would draw from
    // the same register-by-ip / login-by-ip limiter as every other IT class in the same
    // test run, making pass/fail depend on execution order. This class isn't testing
    // rate limiting (RateLimitingServiceTest does), so it shouldn't be subject to it.
    @MockitoBean
    private RateLimitingService rateLimitingService;

    private static RegistrationRequest ownerRequest(String email, String orgName) {
        return new RegistrationRequest(
                email, "Password123!", "Password123!", null,
                "Doe", "John", null,
                orgName, null, null
        );
    }

    private MvcResult registerOwner(String email, String orgName) throws Exception {
        return mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ownerRequest(email, orgName))))
                .andReturn();
    }

    // ---- register -----------------------------------------------------

    @Test
    void register_owner_returns201WithCookiesLocationAndContext() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ownerRequest("owner1@sentio.dev", "Acme Legal"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(".*/users/\\d+$")))
                .andExpect(jsonPath("$.email").value("owner1@sentio.dev"))
                .andExpect(jsonPath("$.orgName").value("Acme Legal"))
                .andExpect(jsonPath("$.orgRole").value("OWNER"))
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().httpOnly("access_token", true))
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().httpOnly("refresh_token", true));
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        registerOwner("owner2@sentio.dev", "Acme Legal");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ownerRequest("owner2@sentio.dev", "Other Firm"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_ALREADY_EXISTS"));
    }

    @Test
    void register_shortPassword_returns400ValidationFailed() throws Exception {
        RegistrationRequest invalid = new RegistrationRequest(
                "owner3@sentio.dev", "short", "short", null,
                "Doe", "John", null,
                "Acme Legal", null, null
        );

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ---- login ----------------------------------------------------------

    @Test
    void login_validCredentials_returns200WithFreshCookies() throws Exception {
        registerOwner("owner4@sentio.dev", "Acme Legal");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("owner4@sentio.dev", "Password123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("owner4@sentio.dev"))
                .andExpect(jsonPath("$.orgName").value("Acme Legal"))
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().exists("refresh_token"));
    }

    @Test
    void login_wrongPasswordAndUnknownEmail_return401WithIdenticalMessage() throws Exception {
        registerOwner("owner5@sentio.dev", "Acme Legal");

        MvcResult wrongPassword = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("owner5@sentio.dev", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        MvcResult unknownEmail = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("ghost@sentio.dev", "whatever123"))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        // Enumeration protection: the two failure modes must be indistinguishable to the
        // caller. Compare code/message only - timestamp legitimately differs per request.
        ErrorResponse wrongPasswordBody = objectMapper.readValue(
                wrongPassword.getResponse().getContentAsString(), ErrorResponse.class);
        ErrorResponse unknownEmailBody = objectMapper.readValue(
                unknownEmail.getResponse().getContentAsString(), ErrorResponse.class);

        assertThat(wrongPasswordBody.code()).isEqualTo(unknownEmailBody.code());
        assertThat(wrongPasswordBody.message()).isEqualTo(unknownEmailBody.message());
    }

    // ---- refresh --------------------------------------------------------

    @Test
    void refresh_withoutCookie_returns401() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_validCookie_rotatesTokensAndReturns204() throws Exception {
        MvcResult registerResult = registerOwner("owner6@sentio.dev", "Acme Legal");
        MockHttpServletResponse registerResponse = registerResult.getResponse();
        String originalRefreshToken = registerResponse.getCookie("refresh_token").getValue();

        MvcResult refreshResult = mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", originalRefreshToken)))
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
        MvcResult registerResult = registerOwner("owner7@sentio.dev", "Acme Legal");
        String originalRefreshToken = registerResult.getResponse().getCookie("refresh_token").getValue();

        // First refresh: rotates and revokes the original token - succeeds.
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", originalRefreshToken)))
                .andExpect(status().isNoContent());

        // Reusing the now-revoked original token must be rejected, not silently accepted.
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", originalRefreshToken)))
                .andExpect(status().isUnauthorized());
    }

    // ---- logout -----------------------------------------------------------

    @Test
    void logout_clearsCookiesAndRevokesRefreshTokenServerSide() throws Exception {
        MvcResult registerResult = registerOwner("owner8@sentio.dev", "Acme Legal");
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
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_withoutAnyCookies_stillReturns204() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isNoContent());
    }
}

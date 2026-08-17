package com.sentio.user_service.user;

import tools.jackson.databind.ObjectMapper;
import com.sentio.user_service.TestcontainersConfiguration;
import com.sentio.user_service.auth.dto.RegistrationRequest;
import com.sentio.user_service.auth.rate_limiting.RateLimitingService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SEN-73: soft-deleting a user must (a) free up their email for re-registration
 * and (b) immediately invalidate their session, not just their DB row.
 * <p>
 * Relies on: {@code User}'s {@code @SQLRestriction("deleted_at IS NULL")},
 * the {@code users_email_active_idx} partial unique index (migration V6),
 * and {@code UserService.deleteUser} blacklisting the caller's access token.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserSoftDeleteIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RateLimitingService rateLimitingService;

    // See OrganizationIsolationIT - CookieCsrfTokenRepository double-submit pattern,
    // every response carries a fresh XSRF-TOKEN cookie that state-changing requests
    // must echo back as the X-XSRF-TOKEN header.
    private record Session(String accessToken, String xsrfToken) {}

    private Session register(String email, String phoneNumber) throws Exception {
        RegistrationRequest request = new RegistrationRequest(
                email, "Password123!", "Password123!", phoneNumber,
                "Doe", "John", null,
                null, null, null
        );
        MockHttpServletResponse response = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse();
        return new Session(
                response.getCookie("access_token").getValue(),
                response.getCookie("XSRF-TOKEN").getValue()
        );
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder builder, Session session) {
        return builder
                .cookie(new Cookie("access_token", session.accessToken()), new Cookie("XSRF-TOKEN", session.xsrfToken()))
                .header("X-XSRF-TOKEN", session.xsrfToken());
    }

    @Test
    void deletedUsersAccessToken_isBlacklisted_soProtectedEndpointReturns401() throws Exception {
        Session session = register("blacklist-me@sentio.dev", "+380501112233");

        mockMvc.perform(authenticated(delete("/users/me"), session))
                .andExpect(status().isNoContent());

        // The cookie is still cryptographically valid (not expired) - only the
        // blacklist should be rejecting it now.
        mockMvc.perform(authenticated(get("/users/me"), session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reRegistrationWithSameEmailAfterSoftDelete_succeeds() throws Exception {
        String email = "reuse-me@sentio.dev";
        Session firstSession = register(email, "+380504445566");

        mockMvc.perform(authenticated(delete("/users/me"), firstSession))
                .andExpect(status().isNoContent());

        // Same email, different phone number (that column is still a plain
        // unique constraint, unrelated to this scenario).
        register(email, "+380507778899");
    }
}

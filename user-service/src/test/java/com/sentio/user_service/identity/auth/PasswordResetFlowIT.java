package com.sentio.user_service.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sentio.user_service.TestcontainersConfiguration;
import com.sentio.user_service.identity.auth.dto.request.LoginRequest;
import com.sentio.user_service.identity.auth.dto.request.PasswordResetConfirmRequest;
import com.sentio.user_service.identity.auth.dto.request.PasswordResetRequest;
import com.sentio.user_service.identity.auth.dto.request.RegistrationRequest;
import com.sentio.user_service.identity.auth.rate_limiting.RateLimitingService;
import com.sentio.user_service.identity.auth.service.AuthService;
import com.sentio.user_service.notification.internal.mail.EmailSender;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end coverage of the password reset flow: request -> email -> confirm with a new
 * password, and the two properties that matter most for a security-sensitive flow like this one -
 * no user enumeration via the request endpoint, and every other active session is killed the
 * moment the password actually changes.
 *
 * <p>{@link EmailSender} and {@link RateLimitingService} are mocked for the same reasons as in
 * {@code EmailVerificationFlowIT}: nothing here runs a real SMTP server, and real Redis-backed
 * limits aren't what's under test.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetFlowIT {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("token=([^\"&]+)");
    private static final String OLD_PASSWORD = "OldPassword123!";
    private static final String NEW_PASSWORD = "NewPassword123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private com.sentio.user_service.identity.auth.password_reset.PasswordResetService passwordResetService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private RateLimitingService rateLimitingService;

    @MockitoBean
    private EmailSender emailSender;

    private record Session(String accessToken, String refreshToken) {}

    private Session register(String email) throws Exception {
        RegistrationRequest request =
                new RegistrationRequest(email, OLD_PASSWORD, OLD_PASSWORD, null, "Doe", "John", null);
        MockHttpServletResponse response = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse();

        // Registration itself asynchronously sends an email-verification email to this same
        // address - wait for it, then wipe the mock's history so a later password-reset-specific
        // assertion in the same test can't race against it or mistake it for the reset email.
        verify(emailSender, timeout(5000)).sendEmail(any(), eq(email), anyString(), anyString());
        clearInvocations(emailSender);

        return new Session(
                response.getCookie("access_token").getValue(),
                response.getCookie("refresh_token").getValue());
    }

    private void requestReset(String email) throws Exception {
        mockMvc.perform(post("/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetRequest(email))))
                .andExpect(status().isNoContent());
    }

    private String awaitRawTokenFromTheSentEmail(String email) {
        ArgumentCaptor<String> htmlBody = ArgumentCaptor.forClass(String.class);
        verify(emailSender, timeout(5000)).sendEmail(any(), eq(email), anyString(), htmlBody.capture());

        Matcher matcher = TOKEN_PATTERN.matcher(htmlBody.getValue());
        assertThat(matcher.find()).as("reset link with a token in the sent email body").isTrue();
        return matcher.group(1);
    }

    private void confirmReset(String rawToken, String newPassword) throws Exception {
        mockMvc.perform(post("/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PasswordResetConfirmRequest(rawToken, newPassword, newPassword))))
                .andExpect(status().isNoContent());
    }

    private void login(String email, String password, org.springframework.test.web.servlet.ResultMatcher expectedStatus)
            throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(expectedStatus);
    }

    @Test
    void requestReset_confirmWithNewPassword_oldPasswordStopsWorking_oldSessionIsRevoked() throws Exception {
        String email = "reset-flow@sentio.dev";
        Session session = register(email);

        requestReset(email);
        String rawToken = awaitRawTokenFromTheSentEmail(email);
        confirmReset(rawToken, NEW_PASSWORD);

        login(email, OLD_PASSWORD, status().isUnauthorized());
        login(email, NEW_PASSWORD, status().isOk());

        // The refresh token issued at registration - i.e. before the password was reset -
        // must no longer work, or resetting a compromised password wouldn't actually help.
        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", session.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void confirmWithAnAlreadyConsumedToken_isRejectedOnSecondAttempt() throws Exception {
        String email = "reset-reuse@sentio.dev";
        register(email);

        requestReset(email);
        String rawToken = awaitRawTokenFromTheSentEmail(email);
        confirmReset(rawToken, NEW_PASSWORD);

        mockMvc.perform(post("/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PasswordResetConfirmRequest(rawToken, "AnotherPassword123!", "AnotherPassword123!"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requestReset_unknownEmail_respondsIdenticallyAndSendsNoEmail() throws Exception {
        requestReset("nobody-at-all@sentio.dev");

        // No commit-then-async-listener sequence to await here (nothing was ever published) -
        // just a bounded window to prove silence, not just "hasn't happened yet".
        await().pollDelay(Duration.ofSeconds(2))
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> verifyNoInteractions(emailSender));
    }

    @Test
    void requestResetRolledBack_neverSendsTheEmail() throws Exception {
        // The account itself must exist and commit first - only requestReset()'s own
        // transaction is the one rolled back below.
        String email = "reset-rolled-back@sentio.dev";
        register(email);

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> {
            passwordResetService.requestReset(email, ZoneId.of("Europe/Kyiv"));
            status.setRollbackOnly();
        });

        await().pollDelay(Duration.ofSeconds(2))
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> verifyNoInteractions(emailSender));
    }
}

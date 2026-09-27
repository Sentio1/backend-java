package com.sentio.user_service.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sentio.user_service.TestcontainersConfiguration;
import com.sentio.user_service.identity.auth.dto.request.RegistrationRequest;
import com.sentio.user_service.identity.auth.dto.request.VerifyEmailRequest;
import com.sentio.user_service.identity.auth.oauth.GoogleAccountResolver;
import com.sentio.user_service.identity.auth.oauth.dto.GoogleIdentity;
import com.sentio.user_service.identity.auth.rate_limiting.RateLimitingService;
import com.sentio.user_service.identity.auth.service.AuthService;
import com.sentio.user_service.identity.user.internal.repository.UserRepository;
import com.sentio.user_service.notification.internal.mail.EmailSender;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end coverage of SEN-73's email verification (see {@code plan_email.txt}): registration
 * fires the {@code EmailVerificationRequested} event, the (async, post-commit) listener renders
 * and "sends" the email, confirming that link marks the account verified, and - the whole reason
 * verification exists - a Google sign-in can only auto-link onto that account once it is.
 *
 * <p>{@link EmailSender} is mocked rather than pointed at a real SMTP server: nothing in this repo
 * runs one, and the point of this test is the verification flow, not JavaMail wiring (that's
 * {@code EmailSenderTest}'s job). {@link RateLimitingService} is mocked the same way every other
 * flow IT in this package does it - real Redis-backed limits aren't what's under test here either.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class EmailVerificationFlowIT {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("token=([^\"&]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GoogleAccountResolver googleAccountResolver;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private AuthService authService;

    @MockitoBean
    private RateLimitingService rateLimitingService;

    @MockitoBean
    private EmailSender emailSender;

    private void register(String email) throws Exception {
        RegistrationRequest request =
                new RegistrationRequest(email, "Password123!", "Password123!", null, "Doe", "John", null);
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private String awaitRawTokenFromTheSentEmail(String email) {
        ArgumentCaptor<String> htmlBody = ArgumentCaptor.forClass(String.class);
        // The listener runs async, after the registration transaction commits - a plain
        // verify() would race it, so wait (with a retry timeout) instead of asserting instantly.
        verify(emailSender, timeout(5000)).sendEmail(any(), eq(email), anyString(), htmlBody.capture());

        Matcher matcher = TOKEN_PATTERN.matcher(htmlBody.getValue());
        assertThat(matcher.find()).as("confirmation link with a token in the sent email body").isTrue();
        return matcher.group(1);
    }

    @Test
    void registration_email_confirmation_thenGoogleLinking_allSucceed() throws Exception {
        String email = "verify-flow@sentio.dev";
        register(email);

        String rawToken = awaitRawTokenFromTheSentEmail(email);

        mockMvc.perform(post("/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyEmailRequest(rawToken))))
                .andExpect(status().isNoContent());

        long userId = userRepository.findByEmail(email).orElseThrow().getId();
        assertThat(userRepository.findByEmail(email).orElseThrow().getEmailVerifiedAt()).isNotNull();

        // Only reachable because the account above is now verified - see
        // GoogleAccountResolverTest#unverifiedLocalAccountWithSameEmail_isNotLinked for the
        // pre-account-takeover check this would otherwise trip.
        var identity = new GoogleIdentity("google-sub-verify-flow", email, "Jane", "Doe", true);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        var linked = transactionTemplate.execute(status -> googleAccountResolver.resolveOrCreate(identity));

        assertThat(linked.id()).isEqualTo(userId);
    }

    @Test
    void registrationRolledBack_neverSendsTheVerificationEmail() {
        String email = "rolled-back-verify@sentio.dev";
        RegistrationRequest request =
                new RegistrationRequest(email, "Password123!", "Password123!", null, "Doe", "John", null);

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> {
            authService.register(request, "127.0.0.1", "test-agent", ZoneId.of("Europe/Kyiv"));
            // Simulates a failure later in the same request (e.g. an exception after
            // AuthService.register returns but before the controller commits) rolling
            // back everything register() did, verification token included.
            status.setRollbackOnly();
        });

        assertThat(userRepository.findByEmail(email)).isEmpty();

        // The listener is @ApplicationModuleListener (AFTER_COMMIT + async) - there is no commit
        // to wait for, so there is nothing to poll either. Give it a bounded window to prove it
        // silently, not just "hasn't happened yet".
        await().pollDelay(Duration.ofSeconds(2))
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> verifyNoInteractions(emailSender));
    }
}

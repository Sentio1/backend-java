package com.sentio.user_service.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.sentio.user_service.TestcontainersConfiguration;
import com.sentio.user_service.identity.auth.dto.request.RegistrationRequest;
import com.sentio.user_service.identity.auth.password_reset.PasswordResetService;
import com.sentio.user_service.identity.auth.service.AuthService;
import com.sentio.user_service.identity.user.api.dto.IssuedUserActionToken;
import com.sentio.user_service.identity.user.api.enums.UserActionTokenType;
import com.sentio.user_service.identity.user.api.service.UserActionTokenService;
import com.sentio.user_service.identity.user.internal.exception.InvalidUserActionTokenException;
import com.sentio.user_service.identity.user.internal.repository.UserRepository;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Same guarantee as {@code UserActionTokenConcurrencyIT}, but exercised through the real
 * {@link PasswordResetService#resetPassword} path (password hash update + session revocation
 * included), not just the underlying token consumption - a user double-clicking "reset" or
 * opening the reset link in two tabs must not be able to set the password twice from one token.
 *
 * <p>Deliberately NOT class-level {@code @Transactional} - see {@code
 * RefreshTokenConcurrencyIT}'s javadoc for why a shared test transaction would defeat the point.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PasswordResetConcurrencyIT {

    @Autowired
    private AuthService authService;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private UserActionTokenService userActionTokenService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void resetPassword_calledConcurrentlyWithSameRawToken_exactlyOneSucceeds() throws Exception {
        RegistrationRequest request = new RegistrationRequest(
                "concurrent-reset@sentio.dev", "Password123!", "Password123!", null, "Doe", "John", null);
        authService.register(request, "127.0.0.1", "test-agent", ZoneId.of("Europe/Kyiv"));
        long userId = userRepository.findByEmail("concurrent-reset@sentio.dev").orElseThrow().getId();

        IssuedUserActionToken issued =
                userActionTokenService.issueToken(userId, UserActionTokenType.PASSWORD_RESET, Duration.ofMinutes(30));
        String rawToken = issued.rawToken();

        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        Callable<Void> attempt = () -> {
            bothReady.countDown();
            go.await();
            passwordResetService.resetPassword(rawToken, "NewPassword123!");
            return null;
        };

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Void> first = executor.submit(attempt);
            Future<Void> second = executor.submit(attempt);

            assertThat(bothReady.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            int succeeded = 0;
            int rejected = 0;
            for (Future<Void> future : List.of(first, second)) {
                try {
                    future.get(10, TimeUnit.SECONDS);
                    succeeded++;
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(InvalidUserActionTokenException.class);
                    rejected++;
                }
            }

            assertThat(succeeded).isEqualTo(1);
            assertThat(rejected).isEqualTo(1);
        }
    }
}

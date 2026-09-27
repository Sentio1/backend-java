package com.sentio.user_service.identity.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sentio.user_service.TestcontainersConfiguration;
import com.sentio.user_service.identity.auth.dto.request.RegistrationRequest;
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
 * The {@code PESSIMISTIC_WRITE} lock in {@code UserActionTokenRepository#findByHashAndTokenTypeForUpdate}
 * must make a single verification link genuinely single-use even when two requests race on it -
 * e.g. the user double-clicking the confirmation button, or opening the email link in two tabs.
 *
 * <p>Deliberately NOT class-level {@code @Transactional} - see {@code RefreshTokenConcurrencyIT}'s
 * javadoc for why a shared test transaction would defeat the point.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class UserActionTokenConcurrencyIT {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserActionTokenService userActionTokenService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void consumeToken_calledConcurrentlyWithSameRawToken_exactlyOneSucceeds() throws Exception {
        RegistrationRequest request = new RegistrationRequest(
                "concurrent-verify@sentio.dev", "Password123!", "Password123!", null, "Doe", "John", null);
        authService.register(request, "127.0.0.1", "test-agent", ZoneId.of("Europe/Kyiv"));
        long userId = userRepository.findByEmail("concurrent-verify@sentio.dev").orElseThrow().getId();

        // Registration already issued one token as a side effect - issue our own so we
        // have the raw value, which supersedes it (issueToken invalidates prior actives).
        IssuedUserActionToken issued =
                userActionTokenService.issueToken(userId, UserActionTokenType.EMAIL_VERIFICATION, Duration.ofHours(24));
        String rawToken = issued.rawToken();

        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        Callable<Long> attempt = () -> {
            bothReady.countDown();
            go.await();
            return userActionTokenService.consumeToken(rawToken, UserActionTokenType.EMAIL_VERIFICATION);
        };

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Long> first = executor.submit(attempt);
            Future<Long> second = executor.submit(attempt);

            assertThat(bothReady.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            int succeeded = 0;
            int rejected = 0;
            for (Future<Long> future : List.of(first, second)) {
                try {
                    Long resolvedUserId = future.get(10, TimeUnit.SECONDS);
                    assertThat(resolvedUserId).isEqualTo(userId);
                    succeeded++;
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(InvalidUserActionTokenException.class);
                    rejected++;
                }
            }

            // The lock serializes the two calls: whichever gets it first marks the token
            // used, the other then sees it already used instead of also "succeeding" -
            // a real single-use guarantee, not just "usually true under low concurrency".
            assertThat(succeeded).isEqualTo(1);
            assertThat(rejected).isEqualTo(1);
        }
    }
}

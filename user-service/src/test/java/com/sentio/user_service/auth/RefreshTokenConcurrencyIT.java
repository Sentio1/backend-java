package com.sentio.user_service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.lisovskyi.web.error.autoconfigure.standard.UnauthorizedException;
import com.sentio.user_service.TestcontainersConfiguration;
import com.sentio.user_service.auth.dto.request.RegistrationRequest;
import com.sentio.user_service.auth.dto.response.AuthTokens;
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

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RefreshTokenConcurrencyIT {

    @Autowired
    private AuthService authService;

    @Test
    void refresh_calledConcurrentlyWithSameToken_exactlyOneRequestSucceeds() throws Exception {
        String refreshToken = registerAndGetRefreshToken("concurrent-refresh@sentio.dev");

        // Both threads block on the same latch right before calling refresh(), so
        // they arrive at the row lock together instead of racing to get scheduled.
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        Callable<AuthTokens> attempt = () -> {
            bothReady.countDown();
            go.await();
            return authService.refresh(refreshToken, "127.0.0.1", "test-agent");
        };

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<AuthTokens> first = executor.submit(attempt);
            Future<AuthTokens> second = executor.submit(attempt);

            assertThat(bothReady.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            int succeeded = 0;
            int revoked = 0;
            for (Future<AuthTokens> future : List.of(first, second)) {
                try {
                    future.get(10, TimeUnit.SECONDS);
                    succeeded++;
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(UnauthorizedException.class);
                    revoked++;
                }
            }

            // The pessimistic write lock on RefreshTokenRepository.findByTokenHash
            // serializes the two calls: whichever gets the lock first revokes the
            // token and rotates it, the other then sees it already revoked instead
            // of also rotating it - a real single-use guarantee, not just "usually".
            assertThat(succeeded).isEqualTo(1);
            assertThat(revoked).isEqualTo(1);
        }
    }

    private String registerAndGetRefreshToken(String email) {
        RegistrationRequest request =
                new RegistrationRequest(email, "Password123!", "Password123!", null, "Doe", "John", null);
        return authService
                .register(request, "127.0.0.1", "test-agent")
                .authTokens()
                .refreshToken();
    }
}

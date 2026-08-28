package com.sentio.user_service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceAlreadyExistsException;
import com.sentio.user_service.TestcontainersConfiguration;
import com.sentio.user_service.auth.dto.response.AuthResult;
import com.sentio.user_service.auth.dto.request.RegistrationRequest;
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
 * PR review fix (coderabbit + Dashulya-coder): "Make registration idempotent under concurrent
 * requests." {@link AuthService#register} checks {@code existsByEmail} then inserts as two
 * separate steps - two requests for the same email can both pass the check. The fix translates
 * the resulting {@code DataIntegrityViolationException} from {@code users_email_active_idx}
 * (migration V6) into a clean {@link ResourceAlreadyExistsException} instead of leaking a raw
 * 500. This only works if the constraint name the catch block matches against is the *real* one
 * - a stale/wrong name (as originally written: {@code "uq_users_email"}, which V6 replaced with
 * {@code users_email_active_idx}) would always fall through to {@code throw e} and defeat the
 * whole fix.
 *
 * <p>Deliberately NOT class-level {@code @Transactional} - see {@code
 * RefreshTokenConcurrencyIT}'s javadoc for why a shared test transaction would defeat the point.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AuthServiceRegisterConcurrencyIT {

    @Autowired
    private AuthService authService;

    @Test
    void register_calledConcurrentlyWithSameEmail_exactlyOneSucceedsAndTheLoserGetsACleanConflict() throws Exception {
        RegistrationRequest request = new RegistrationRequest(
                "concurrent-register@sentio.dev", "Password123!", "Password123!", null, "Doe", "John", null);

        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        Callable<AuthResult> attempt = () -> {
            bothReady.countDown();
            go.await();
            return authService.register(request, "127.0.0.1", "test-agent");
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<AuthResult> first = executor.submit(attempt);
            Future<AuthResult> second = executor.submit(attempt);

            assertThat(bothReady.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            int succeeded = 0;
            int conflicted = 0;
            for (Future<AuthResult> future : List.of(first, second)) {
                try {
                    future.get(10, TimeUnit.SECONDS);
                    succeeded++;
                } catch (ExecutionException e) {
                    // The whole point of the fix: a clean, typed conflict, not a raw
                    // DataIntegrityViolationException/500 from an unmatched constraint name.
                    assertThat(e.getCause()).isInstanceOf(ResourceAlreadyExistsException.class);
                    conflicted++;
                }
            }

            assertThat(succeeded).isEqualTo(1);
            assertThat(conflicted).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }
}

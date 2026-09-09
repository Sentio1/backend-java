package com.sentio.user_service.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.sentio.user_service.TestcontainersConfiguration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PR review fix (Dashulya-coder): "Race on first-time Google sign-in - unhandled 500". Two
 * concurrent requests for the same brand-new Google identity both pass {@code
 * existingIdentity.isPresent() == false}, both try to create the {@link User} row - the second
 * one collides with the first on the {@code users.email} partial unique index. {@link
 * GoogleAccountResolver} is expected to catch that (via {@link GoogleNewUserCreator}'s own
 * transaction - see its javadoc for why a plain try/catch in the caller's transaction isn't
 * enough on Postgres) and resolve to the winner's account instead of leaking a raw
 * {@code DataIntegrityViolationException} (=~ 500) to the loser.
 *
 * <p>Deliberately calls {@link GoogleAccountResolver} directly, not through {@code
 * AuthService.loginOrRegisterWithGoogle} - that method has its own, separate concurrency issue
 * one step further down (concurrent org/default-membership provisioning), which this test isn't
 * about. Also deliberately NOT class-level {@code @Transactional} - see {@code
 * RefreshTokenConcurrencyIT}'s javadoc for why a shared test transaction would defeat the point.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class GoogleSignInConcurrencyIT {

    @Autowired
    private GoogleAccountResolver googleAccountResolver;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void resolveOrCreate_calledConcurrentlyForNewIdentity_bothRequestsResolveToTheSameUser() throws Exception {
        var identity = new com.sentio.user_service.auth.oauth.dto.GoogleIdentity(
                "google-sub-concurrent-1", "concurrent-google@sentio.dev", "Concurrent", "Tester", true);

        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        // Each thread runs resolveOrCreate() inside its own transaction (mirroring
        // AuthService's @Transactional boundary in production), so the lazily-loaded
        // User behind resolveExistingAfterConflict's UserIdentity->User relation can
        // still be read (.getId()) before that transaction/session closes.
        Callable<Long> attempt = () -> {
            bothReady.countDown();
            go.await();
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            return transactionTemplate.execute(
                    status -> googleAccountResolver.resolveOrCreate(identity).getId());
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Long> first = executor.submit(attempt);
            Future<Long> second = executor.submit(attempt);

            assertThat(bothReady.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            List<Long> resolvedUserIds = List.of(first, second).stream()
                    .map(f -> {
                        try {
                            return f.get(10, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            throw new AssertionError("Concurrent Google sign-in threw instead of resolving", e);
                        }
                    })
                    .toList();

            // Both requests must succeed (no unhandled DataIntegrityViolationException),
            // and both must land on the very same account - not two duplicate users
            // for what is really one person signing in twice at once.
            assertThat(resolvedUserIds).hasSize(2);
            assertThat(resolvedUserIds.get(0)).isEqualTo(resolvedUserIds.get(1));
        } finally {
            executor.shutdownNow();
        }
    }
}

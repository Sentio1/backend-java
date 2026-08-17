package com.sentio.user_service.auth.rate_limiting;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Real RateLimiterRegistry with tiny, test-only limits (no mocking of Resilience4j internals) -
 * exercises the actual permission-acquisition logic instead of just verifying that some method got
 * called. Named configs mirror application.yaml's
 * resilience4j.ratelimiter.configs.{login,register}-by-{email,ip}.
 */
class RateLimitingServiceTest {

    private RateLimitingService rateLimitingService;

    @BeforeEach
    void setUp() {
        RateLimiterConfig onePerMinute = RateLimiterConfig.custom()
                .limitForPeriod(1)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ZERO)
                .build();

        RateLimiterRegistry registry = RateLimiterRegistry.custom()
                .addRateLimiterConfig("login-by-email", onePerMinute)
                .addRateLimiterConfig("login-by-ip", onePerMinute)
                .addRateLimiterConfig("register-by-email", onePerMinute)
                .addRateLimiterConfig("register-by-ip", onePerMinute)
                .build();

        Cache<String, RateLimiter> limitersCache = Caffeine.newBuilder().build();

        rateLimitingService = new RateLimitingService(limitersCache, registry);
    }

    @Test
    void firstLoginAttempt_isPermitted() {
        assertThatCode(() -> rateLimitingService.checkLoginLimits("user@sentio.dev", "127.0.0.1"))
                .doesNotThrowAnyException();
    }

    @Test
    void secondAttemptWithSameEmail_isRejectedByEmailLimiter() {
        rateLimitingService.checkLoginLimits("user@sentio.dev", "1.1.1.1");

        assertThatThrownBy(() -> rateLimitingService.checkLoginLimits("user@sentio.dev", "2.2.2.2"))
                .isInstanceOf(RequestNotPermitted.class);
    }

    @Test
    void secondAttemptFromSameIp_isRejectedByIpLimiter() {
        rateLimitingService.checkLoginLimits("first@sentio.dev", "3.3.3.3");

        assertThatThrownBy(() -> rateLimitingService.checkLoginLimits("second@sentio.dev", "3.3.3.3"))
                .isInstanceOf(RequestNotPermitted.class);
    }

    @Test
    void emailMatchIsCaseInsensitive() {
        rateLimitingService.checkLoginLimits("User@Sentio.dev", "4.4.4.4");

        assertThatThrownBy(() -> rateLimitingService.checkLoginLimits("user@sentio.dev", "5.5.5.5"))
                .isInstanceOf(RequestNotPermitted.class);
    }

    @Test
    void loginAndRegisterLimitsAreIndependent() {
        rateLimitingService.checkLoginLimits("user@sentio.dev", "6.6.6.6");

        // register uses different rate-limiter keys ("register-by-email"/"register-by-ip"),
        // so it isn't affected by the login attempt above.
        assertThatCode(() -> rateLimitingService.checkRegisterLimits("user@sentio.dev", "6.6.6.6"))
                .doesNotThrowAnyException();
    }

    @Test
    void registerLimits_sameRulesApply() {
        rateLimitingService.checkRegisterLimits("new@sentio.dev", "7.7.7.7");

        assertThatThrownBy(() -> rateLimitingService.checkRegisterLimits("new@sentio.dev", "8.8.8.8"))
                .isInstanceOf(RequestNotPermitted.class);
    }
}

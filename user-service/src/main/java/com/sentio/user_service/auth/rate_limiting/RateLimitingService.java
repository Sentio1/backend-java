package com.sentio.user_service.auth.rate_limiting;

import com.github.benmanes.caffeine.cache.Cache;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
/** RateLimitingService class. */
public class RateLimitingService {

    private final Cache<String, RateLimiter> limitersCache;
    private final RateLimiterRegistry rateLimiterRegistry;

    public void checkLoginLimits(String email, String ip) {
        checkLimits("login", email, ip);
    }

    public void checkRegisterLimits(String email, String ip) {
        checkLimits("register", email, ip);
    }

    private void checkLimits(String action, String email, String ip) {
        if (email == null || email.isBlank()) {
            return;
        }

        if (ip == null || ip.isBlank()) {
            return;
        }

        String emailConfig = action + "-by-email";
        String emailKey = String.format("%s-email:%s", action, email.toLowerCase(Locale.ROOT));

        RateLimiter emailRateLimiter =
                limitersCache.get(emailKey, key -> RateLimiter.of(key, resolveConfig(emailConfig)));

        if (!emailRateLimiter.acquirePermission()) {
            throw RequestNotPermitted.createRequestNotPermitted(emailRateLimiter);
        }

        String ipConfig = action + "-by-ip";
        String ipKey = String.format("%s-ip:%s", action, ip);

        RateLimiter ipRateLimiter = limitersCache.get(ipKey, key -> RateLimiter.of(key, resolveConfig(ipConfig)));

        if (!ipRateLimiter.acquirePermission()) {
            throw RequestNotPermitted.createRequestNotPermitted(ipRateLimiter);
        }
    }

    private RateLimiterConfig resolveConfig(String configName) {
        return rateLimiterRegistry
                .getConfiguration(configName)
                .orElseThrow(() -> new IllegalStateException("No rate limiter config: " + configName));
    }
}

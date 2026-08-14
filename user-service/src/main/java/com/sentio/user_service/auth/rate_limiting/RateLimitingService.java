package com.sentio.user_service.auth.rate_limiting;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RateLimitingService {

    private final RateLimiterRegistry rateLimiterRegistry;

    public void checkLoginLimits(String email, String ip) {
        checkLimits("login", email, ip);
    }

    public void checkRegisterLimits(String email, String ip) {
        checkLimits("register", email, ip);
    }

    private void checkLimits(String action, String email, String ip) {
        String emailConfig = action + "-by-email";
        String emailKey = String.format("%s-email:%s", action, email.toLowerCase());

        String ipConfig = action + "-by-ip";
        String ipKey = String.format("%s-ip:%s", action, ip);

        RateLimiter emailRateLimiter = rateLimiterRegistry.rateLimiter(emailKey, emailConfig);
        if (!emailRateLimiter.acquirePermission()) {
            throw RequestNotPermitted.createRequestNotPermitted(emailRateLimiter);
        }

        RateLimiter ipRateLimiter = rateLimiterRegistry.rateLimiter(ipKey, ipConfig);
        if (!ipRateLimiter.acquirePermission()) {
            throw RequestNotPermitted.createRequestNotPermitted(ipRateLimiter);
        }
    }
}

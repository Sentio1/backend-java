package com.sentio.user_service.identity.auth.rate_limiting;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

/**
 * {@code app.rate-limit.rules.<action>-by-<dimension>}: at most {@code limit} attempts per
 * fixed {@code window}, counted in Redis so the limit holds across all replicas.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(Map<String, Rule> rules) {

    public RateLimitProperties {
        rules = rules == null ? Map.of() : Map.copyOf(rules);
    }

    public record Rule(int limit, Duration window) {}
}

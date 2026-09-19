package com.sentio.user_service.identity.auth.rate_limiting;

import com.google.common.hash.Hashing;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Fixed-window rate limiting shared by every replica: one Redis counter per (rule, key), created
 * with the window as its TTL. INCR + PEXPIRE run as one Lua script, so a counter can never be left
 * behind without an expiry.
 *
 * <p>Fails open: if Redis is unreachable the attempt is allowed (and logged at ERROR) - an outage
 * of the limiter shouldn't turn into an outage of login itself.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitingService {

    private static final String KEY_PREFIX = "rate-limit:";

    private static final List<String> REQUIRED_RULES = List.of(
            "login-by-email", "login-by-ip",
            "register-by-email", "register-by-ip",
            "service-token-by-email", "service-token-by-ip",
            "refresh-by-ip");

    // Returns {count, remaining TTL in ms}. The ttl < 0 branch re-arms a key that
    // somehow lost its expiry instead of letting it block that client forever.
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> INCREMENT_SCRIPT = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            local ttl = redis.call('PTTL', KEYS[1])
            if count == 1 or ttl == -1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
                ttl = tonumber(ARGV[1])
            end
            return {count, ttl}
            """, List.class);

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;

    @PostConstruct
    void validateRules() {
        List<String> missing = REQUIRED_RULES.stream()
                .filter(r -> !properties.rules().containsKey(r))
                .toList();

        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing app.rate-limit.rules: " + missing);
        }
    }

    public void checkLoginLimits(String email, String ip) {
        checkLimits("login", email, ip);
    }

    public void checkRegisterLimits(String email, String ip) {
        checkLimits("register", email, ip);
    }

    public void checkServiceTokenLimits(String clientId, String ip) {
        checkLimits("service-token", clientId, ip);
    }

    public void checkRefreshLimits(String ip) {
        checkLimits("refresh", null, ip);
    }

    private void checkLimits(String action, String email, String ip) {
        if (StringUtils.hasText(ip)) {
            check(action + "-by-ip", ip.strip());
        } else {
            log.warn("Rate limiter: could not resolve client IP for action {}", action);
        }

        if (StringUtils.hasText(email)) {
            check(action + "-by-email", sha256(email.toLowerCase(Locale.ROOT)));
        }
    }

    private void check(String ruleName, String value) {
        RateLimitProperties.Rule rule = properties.rules().get(ruleName);
        if (rule == null) {
            log.error("Rule configuration not found for: {}", ruleName);
            return;
        }

        String key = KEY_PREFIX + ruleName + ":" + value;

        List<?> result;
        try {
            result = redisTemplate.execute(
                    INCREMENT_SCRIPT, List.of(key), String.valueOf(rule.window().toMillis()));
        } catch (DataAccessException e) {
            log.error("Rate limiter unavailable, allowing request (rule: {})", ruleName, e);
            return;
        }

        if (result == null || result.size() < 2) {
            log.error("Unexpected rate limiter script result {}, allowing request (rule: {})", result, ruleName);
            return;
        }

        long count = ((Number) result.get(0)).longValue();
        if (count > rule.limit()) {
            long ttlMillis = ((Number) result.get(1)).longValue();
            log.warn("Rate limit exceeded: {} ({} attempts in current window)", ruleName, count);
            throw new RateLimitExceededException(ruleName, Duration.ofMillis(Math.max(ttlMillis, 0)));
        }
    }

    private static String sha256(String value) {
        return Hashing.sha256()
                .hashString(value, StandardCharsets.UTF_8)
                .toString();
    }
}

package com.sentio.user_service.identity.auth.rate_limiting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Real Redis, no Spring context - the Lua script (INCR + PEXPIRE + PTTL) is the part worth testing,
 * and mocking RedisTemplate.execute would test nothing but the mock. Tiny test-only limits: one
 * attempt per rule per window.
 */
@Testcontainers
class RateLimitingServiceIT {

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    private RateLimitingService rateLimitingService;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

        RateLimitProperties.Rule onePerMinute = new RateLimitProperties.Rule(1, Duration.ofMinutes(1));
        RateLimitProperties properties = new RateLimitProperties(Map.of(
                "login-by-email", onePerMinute,
                "login-by-ip", onePerMinute,
                "register-by-email", onePerMinute,
                "register-by-ip", onePerMinute,
                "service-token-by-email", onePerMinute,
                "service-token-by-ip", onePerMinute,
                "refresh-by-ip", onePerMinute));

        rateLimitingService = new RateLimitingService(redisTemplate, properties);
        rateLimitingService.validateRules();
    }

    @Test
    void firstLoginAttempt_isPermitted() {
        assertThatCode(() -> rateLimitingService.checkLoginLimits("user@sentio.dev", "127.0.0.1"))
                .doesNotThrowAnyException();
    }

    @Test
    void secondAttemptWithSameEmail_isRejected_withRetryAfterWithinTheWindow() {
        rateLimitingService.checkLoginLimits("user@sentio.dev", "1.1.1.1");

        assertThatThrownBy(() -> rateLimitingService.checkLoginLimits("user@sentio.dev", "2.2.2.2"))
                .isInstanceOfSatisfying(RateLimitExceededException.class, ex -> assertThat(ex.getRetryAfter())
                        .isPositive()
                        .isLessThanOrEqualTo(Duration.ofMinutes(1)));
    }

    @Test
    void secondAttemptFromSameIp_isRejected() {
        rateLimitingService.checkLoginLimits("first@sentio.dev", "3.3.3.3");

        assertThatThrownBy(() -> rateLimitingService.checkLoginLimits("second@sentio.dev", "3.3.3.3"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void emailMatchIsCaseInsensitive() {
        rateLimitingService.checkLoginLimits("User@Sentio.dev", "4.4.4.4");

        assertThatThrownBy(() -> rateLimitingService.checkLoginLimits("user@sentio.dev", "5.5.5.5"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void loginAndRegisterLimitsAreIndependent() {
        rateLimitingService.checkLoginLimits("user@sentio.dev", "6.6.6.6");

        assertThatCode(() -> rateLimitingService.checkRegisterLimits("user@sentio.dev", "6.6.6.6"))
                .doesNotThrowAnyException();
    }

    @Test
    void blockedIpDoesNotConsumeEmailAttempt() {
        rateLimitingService.checkLoginLimits("user1@sentio.dev", "9.9.9.9");

        assertThatThrownBy(() -> rateLimitingService.checkLoginLimits("user2@sentio.dev", "9.9.9.9"))
                .isInstanceOf(RateLimitExceededException.class);

        // The IP check failed first, so user2's email counter was never touched.
        assertThatCode(() -> rateLimitingService.checkLoginLimits("user2@sentio.dev", "10.10.10.10"))
                .doesNotThrowAnyException();
    }

    @Test
    void countersExpireWithTheirWindow_soNoKeyIsLeftWithoutTtl() {
        rateLimitingService.checkRefreshLimits("11.11.11.11");

        Long ttl = redisTemplate.getExpire("rate-limit:refresh-by-ip:11.11.11.11");
        assertThat(ttl).isPositive().isLessThanOrEqualTo(60L);
    }

    @Test
    void rawEmailIsNotUsedAsKeyName() {
        rateLimitingService.checkLoginLimits("private@sentio.dev", "12.12.12.12");

        assertThat(redisTemplate.keys("*private@sentio.dev*")).isEmpty();
    }

    @Test
    void missingRule_failsFast() {
        RateLimitingService misconfigured = new RateLimitingService(
                redisTemplate, new RateLimitProperties(Map.of()));

        assertThatThrownBy(misconfigured::validateRules).isInstanceOf(IllegalStateException.class);
    }
}

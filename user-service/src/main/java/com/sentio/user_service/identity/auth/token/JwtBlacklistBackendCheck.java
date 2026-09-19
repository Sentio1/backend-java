package com.sentio.user_service.identity.auth.token;

import com.lisovskyi.security.autoconfigure.security.jwt.JwtBlacklistService;
import com.lisovskyi.security.autoconfigure.security.jwt.RedisJwtBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * The access-token blacklist is part of the cross-service contract (README, "Відкликання access
 * token"): Go services check the same Redis keys, so a logout here has to be visible there. The
 * starter silently falls back to an in-memory blacklist when no StringRedisTemplate is present -
 * which would make logout local to one JVM and invisible to everyone else. Refuse to start instead.
 */
@Component
@Slf4j
@RequiredArgsConstructor
class JwtBlacklistBackendCheck implements InitializingBean {

    private final JwtBlacklistService jwtBlacklistService;

    @Override
    public void afterPropertiesSet() {
        if (!(jwtBlacklistService instanceof RedisJwtBlacklistService)) {
            throw new IllegalStateException(
                    "JWT blacklist must be Redis-backed (shared with other services), got "
                            + jwtBlacklistService.getClass().getName()
                            + " - is Redis configured (spring.data.redis.*)?");
        }
        log.info("JWT blacklist backend: Redis");
    }
}

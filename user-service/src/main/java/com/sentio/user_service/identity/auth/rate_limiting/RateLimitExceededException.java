package com.sentio.user_service.identity.auth.rate_limiting;

import lombok.Getter;

import java.time.Duration;

@Getter
public class RateLimitExceededException extends RuntimeException {

    private final transient Duration retryAfter;

    public RateLimitExceededException(String rule, Duration retryAfter) {
        super("Rate limit exceeded: " + rule);
        this.retryAfter = retryAfter;
    }
}

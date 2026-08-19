package com.sentio.user_service.auth.rate_limiting;

import com.lisovskyi.web.error.autoconfigure.ErrorResponse;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps resilience4j's {@link RequestNotPermitted} (thrown when a rate limiter denies a call) to a
 * 429 response in the same {@link ErrorResponse} shape as every other error in this service. Kept
 * local to user-service rather than added to web-error-spring-boot-starter's
 * GlobalExceptionHandler, so that starter doesn't have to depend on resilience4j-ratelimiter for
 * every consumer that doesn't use rate limiting.
 */
@Slf4j
@RestControllerAdvice
public class RateLimitExceptionHandler {

    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(RequestNotPermitted ex, HttpServletRequest request) {
        log.warn("Rate limit exceeded on path [{}]", request.getRequestURI());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .code("TOO_MANY_REQUESTS")
                .message("Too many attempts. Please try again later.")
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(errorResponse);
    }
}

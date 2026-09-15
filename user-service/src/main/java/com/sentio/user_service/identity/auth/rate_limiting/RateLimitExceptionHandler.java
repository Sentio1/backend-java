package com.sentio.user_service.identity.auth.rate_limiting;

import com.lisovskyi.web.error.autoconfigure.ProblemDetailFactory;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps resilience4j's {@link RequestNotPermitted} (thrown when a rate limiter denies a call) to a
 * 429 response in the same {@link ProblemDetail} shape as every other error in this service. Kept
 * local to user-service rather than added to web-error-spring-boot-starter's
 * GlobalExceptionHandler, so that starter doesn't have to depend on resilience4j-ratelimiter for
 * every consumer that doesn't use rate limiting.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class RateLimitExceptionHandler {

    private final ProblemDetailFactory problemDetailFactory;

    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<ProblemDetail> handleRateLimitExceeded(RequestNotPermitted ex, HttpServletRequest request) {
        log.warn("Rate limit exceeded on path [{}]", request.getRequestURI());

        ProblemDetail problemDetail = problemDetailFactory.create(
                HttpStatus.TOO_MANY_REQUESTS,
                "TOO_MANY_REQUESTS",
                "Too many attempts. Please try again later.",
                request,
                ex);

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(problemDetail);
    }
}

package com.sentio.user_service.identity.auth.rate_limiting;

import com.lisovskyi.web.error.autoconfigure.ProblemDetailFactory;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps {@link RateLimitExceededException} to a 429 in the same {@link ProblemDetail} shape as every
 * other error in this service, plus a {@code Retry-After} header (seconds until the current window
 * resets). Ordered first so the web-error starter's catch-all handler can't claim it.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitExceptionHandler {

    private final ProblemDetailFactory problemDetailFactory;

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleRateLimitExceeded(
            RateLimitExceededException ex, HttpServletRequest request) {
        log.warn("Rate limit exceeded on path [{}]", request.getRequestURI());

        ProblemDetail problemDetail = problemDetailFactory.create(
                HttpStatus.TOO_MANY_REQUESTS,
                "TOO_MANY_REQUESTS",
                "Too many attempts. Please try again later.",
                request,
                ex);

        // Round up - "retry after 0 seconds" for a window with 400 ms left would be a lie.
        long retryAfterSeconds = Math.max(1, (ex.getRetryAfter().toMillis() + 999) / 1000);

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds))
                .body(problemDetail);
    }
}

package com.sentio.user_service.identity.auth.verification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * {@code app.email-verification}: how long a verification token lives and which frontend page the
 * link in the email points to (the token is appended there as a query parameter).
 */
@Validated
@ConfigurationProperties(prefix = "app.email-verification")
public record EmailVerificationProperties(
        @NotNull Duration tokenTTL,
        @NotBlank String verificationUrl
) {}

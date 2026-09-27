package com.sentio.user_service.identity.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import static com.sentio.user_service.identity.auth.verification.EmailVerificationConstants.VERIFY_EMAIL_TOKEN_LENGTH;

public record VerifyEmailRequest(

        @NotBlank
        @Size(max = VERIFY_EMAIL_TOKEN_LENGTH)
        String token
) {}

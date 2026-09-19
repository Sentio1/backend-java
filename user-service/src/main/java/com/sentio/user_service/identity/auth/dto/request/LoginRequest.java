package com.sentio.user_service.identity.auth.dto.request;

import com.sentio.user_service.identity.auth.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import static com.sentio.user_service.identity.auth.AuthConstants.PASSWORD_MAX_BYTES;

public record LoginRequest(

        @NotBlank
        @Email
        String email,

        @NotBlank
        @MaxUtf8Bytes(PASSWORD_MAX_BYTES)
        String password
) {}

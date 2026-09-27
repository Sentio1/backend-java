package com.sentio.user_service.identity.auth.dto.request;

import com.lisovskyi.web.error.autoconfigure.validation.PasswordsMatch;
import com.lisovskyi.web.error.autoconfigure.validation.ValidPassword;
import com.sentio.user_service.identity.auth.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import static com.sentio.user_service.identity.auth.AuthConstants.PASSWORD_MAX_BYTES;
import static com.sentio.user_service.identity.auth.AuthConstants.PASSWORD_MAX_LENGTH;
import static com.sentio.user_service.identity.auth.AuthConstants.PASSWORD_MIN_LENGTH;

@PasswordsMatch(originalPassword = "newPassword", confirmPassword = "confirmPassword")
public record PasswordResetConfirmRequest(

        @NotBlank
        @Size(max = 128)
        String rawToken,

        @NotBlank
        @ValidPassword(minLength = PASSWORD_MIN_LENGTH, maxLength = PASSWORD_MAX_LENGTH)
        @MaxUtf8Bytes(PASSWORD_MAX_BYTES)
        String newPassword,

        @NotBlank
        String confirmPassword
) {}

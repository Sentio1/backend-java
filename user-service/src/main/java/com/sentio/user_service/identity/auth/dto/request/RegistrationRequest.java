package com.sentio.user_service.identity.auth.dto.request;

import com.lisovskyi.web.error.autoconfigure.validation.PasswordsMatch;
import com.lisovskyi.web.error.autoconfigure.validation.ValidPassword;
import com.sentio.user_service.identity.auth.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import static com.sentio.user_service.identity.auth.AuthConstants.*;

// Реєстрація завжди org-less - організацію юзер створює окремим кроком через
// POST /organizations (онбординг-екран) або приєднується по інвайту. Який саме
// шлях показати після реєстрації - суто фронтенд-рішення, бекенду для нього
// нічого знати не треба.
@PasswordsMatch(originalPassword = "password", confirmPassword = "confirmPassword")
public record RegistrationRequest(

        @NotBlank
        @Email
        String email,

        @NotBlank
        @ValidPassword(minLength = PASSWORD_MIN_LENGTH, maxLength = PASSWORD_MAX_LENGTH)
        @MaxUtf8Bytes(PASSWORD_MAX_BYTES)
        String password,

        @NotBlank
        String confirmPassword,

        String phoneNumber,

        @NotBlank
        String lastName,

        @NotBlank
        String firstName,

        String middleName
) {}

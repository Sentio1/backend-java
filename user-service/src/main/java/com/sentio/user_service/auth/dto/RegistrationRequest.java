package com.sentio.user_service.auth.dto;

import static com.sentio.user_service.auth.AuthConstants.*;

import com.lisovskyi.web.error.autoconfigure.validation.PasswordsMatch;
import com.lisovskyi.web.error.autoconfigure.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// Реєстрація завжди org-less - організацію юзер створює окремим кроком через
// POST /organizations (онбординг-екран) або приєднується по інвайту. Який саме
// шлях показати після реєстрації - суто фронтенд-рішення, бекенду для нього
// нічого знати не треба.
@PasswordsMatch(originalPassword = "password", confirmPassword = "confirmPassword")
/** RegistrationRequest record. */
public record RegistrationRequest(
        @NotBlank @Email String email,

        @NotBlank @ValidPassword(minLength = PASSWORD_MIN_LENGTH, maxLength = PASSWORD_MAX_LENGTH)
        String password,

        @NotBlank String confirmPassword,
        String phoneNumber,
        @NotBlank String lastName,
        @NotBlank String firstName,
        String middleName) {}

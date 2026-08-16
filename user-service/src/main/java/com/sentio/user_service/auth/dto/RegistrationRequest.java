package com.sentio.user_service.auth.dto;

import com.lisovskyi.web.error.autoconfigure.validation.PasswordsMatch;
import com.lisovskyi.web.error.autoconfigure.validation.ValidPassword;
import com.sentio.user_service.organization.enums.OrgRole;
import com.sentio.user_service.organization.enums.PlanTier;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import static com.sentio.user_service.auth.AuthConstants.*;
import static com.sentio.user_service.organization.OrganizationConstants.NAME_LENGTH;

@PasswordsMatch(
        originalPassword = "password",
        confirmPassword = "confirmPassword"
)
public record RegistrationRequest(
        // Дані користувача
        @NotBlank
        @Email
        String email,

        @NotBlank
        @ValidPassword(
                minLength = PASSWORD_MIN_LENGTH,
                maxLength = PASSWORD_MAX_LENGTH
        )
        String password,
        @NotBlank String confirmPassword,

        String phoneNumber,

        @NotBlank String lastName,
        @NotBlank String firstName,
        String middleName,

        // Дані організації
        @Size(max = NAME_LENGTH)
        String name,
        String edrpou,

        // для пошуку організації
        String slug,

        PlanTier plan,

        // Дані члена організації
        @NotNull OrgRole orgRole
) {}

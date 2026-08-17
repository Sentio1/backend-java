package com.sentio.user_service.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** LoginRequest record. */
public record LoginRequest(
        @NotBlank @Email String email, @NotBlank String password) {}

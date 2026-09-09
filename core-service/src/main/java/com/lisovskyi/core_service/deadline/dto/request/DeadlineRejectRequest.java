package com.lisovskyi.core_service.deadline.dto.request;

import jakarta.validation.constraints.NotBlank;

public record DeadlineRejectRequest(@NotBlank String reason) {}

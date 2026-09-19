package com.sentio.core_service.deadline.internal.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record DeadlineRejectRequest(@NotBlank String reason) {}

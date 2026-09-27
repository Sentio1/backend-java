package com.sentio.core_service.litigation.internal.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record CaseEventOccurredAtChangeRequest(
        @NotNull Instant newOccurredAt, @NotBlank String reason) {}

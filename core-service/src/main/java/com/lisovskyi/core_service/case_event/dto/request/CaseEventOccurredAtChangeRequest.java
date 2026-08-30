package com.lisovskyi.core_service.case_event.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record CaseEventOccurredAtChangeRequest(
        @NotNull Instant newOccurredAt, @NotBlank String reason) {}

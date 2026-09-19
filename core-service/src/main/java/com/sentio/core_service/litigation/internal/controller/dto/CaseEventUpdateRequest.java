package com.sentio.core_service.litigation.internal.controller.dto;

import static com.sentio.core_service.litigation.internal.CaseEventConstants.TITLE_LENGTH;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.openapitools.jackson.nullable.JsonNullable;

public record CaseEventUpdateRequest(
        JsonNullable<@NotBlank @Size(max = TITLE_LENGTH) String> title, JsonNullable<String> description) {}

package com.lisovskyi.core_service.case_event.dto.request;

import static com.lisovskyi.core_service.case_event.CaseEventConstants.TITLE_LENGTH;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.openapitools.jackson.nullable.JsonNullable;

public record CaseEventUpdateRequest(
        JsonNullable<@NotBlank @Size(max = TITLE_LENGTH) String> title, JsonNullable<String> description) {}

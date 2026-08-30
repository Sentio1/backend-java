package com.lisovskyi.core_service.case_event.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.openapitools.jackson.nullable.JsonNullable;

import static com.lisovskyi.core_service.case_event.CaseEventConstants.TITLE_LENGTH;

public record CaseEventUpdateRequest(
        JsonNullable<@NotBlank @Size(max = TITLE_LENGTH) String> title,

        JsonNullable<String> description
) {}

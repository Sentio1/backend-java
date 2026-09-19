package com.sentio.core_service.litigation.internal.controller.dto;

import static com.sentio.core_service.litigation.internal.CaseConstants.*;

import com.sentio.core_service.litigation.api.enums.CaseInstance;
import com.sentio.core_service.litigation.api.enums.ProcedureType;
import com.sentio.core_service.litigation.internal.validation.CaseNumber;
import com.sentio.shared.util.StringNormalization;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.openapitools.jackson.nullable.JsonNullable;

public record CaseCreateRequest(
        JsonNullable<@CaseNumber String> caseNumber,

        @NotBlank @Size(max = TITLE_LENGTH) String title,

        @NotNull ProcedureType procedure,

        @NotNull CaseInstance instance,

        JsonNullable<@Size(max = JUDGE_NAME_LENGTH) String> judgeName,

        JsonNullable<Long> courtId,

        JsonNullable<@Size(max = INTERNAL_NUMBER_LENGTH) String> internalNumber,

        @NotNull Long responsibleUserId) {
    public CaseCreateRequest {
        caseNumber = StringNormalization.blankToNull(caseNumber);
        internalNumber = StringNormalization.blankToNull(internalNumber);
        judgeName = StringNormalization.blankToNull(judgeName);
    }
}

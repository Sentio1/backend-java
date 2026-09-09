package com.lisovskyi.core_service.case_.dto.request;

import static com.lisovskyi.core_service.case_.CaseConstants.*;

import com.lisovskyi.core_service.case_.enums.CaseInstance;
import com.lisovskyi.core_service.case_.enums.ProcedureType;
import com.lisovskyi.core_service.case_.validation.CaseNumber;
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

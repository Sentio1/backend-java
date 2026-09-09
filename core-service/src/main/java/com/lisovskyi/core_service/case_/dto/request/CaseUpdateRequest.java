package com.lisovskyi.core_service.case_.dto.request;

import static com.lisovskyi.core_service.case_.CaseConstants.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lisovskyi.core_service.case_.enums.CaseInstance;
import com.lisovskyi.core_service.case_.enums.CaseStatus;
import com.lisovskyi.core_service.case_.enums.ProcedureType;
import com.lisovskyi.core_service.case_.validation.CaseNumber;
import com.sentio.shared.util.StringNormalization;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.openapitools.jackson.nullable.JsonNullable;

public record CaseUpdateRequest(
        JsonNullable<@CaseNumber String> caseNumber,

        JsonNullable<@Size(max = TITLE_LENGTH) String> title,

        JsonNullable<@Size(max = INTERNAL_NUMBER_LENGTH) String> internalNumber,

        JsonNullable<ProcedureType> procedure,

        JsonNullable<CaseInstance> instance,

        JsonNullable<CaseStatus> status,

        JsonNullable<Long> courtId,

        JsonNullable<@Size(max = JUDGE_NAME_LENGTH) String> judgeName,

        JsonNullable<Long> responsibleUserId,

        JsonNullable<LocalDate> openedAt,

        JsonNullable<LocalDate> closedAt,

        JsonNullable<Boolean> registryWatchEnabled) {
    public CaseUpdateRequest {
        caseNumber = StringNormalization.blankToNull(caseNumber);
        internalNumber = StringNormalization.blankToNull(internalNumber);
        judgeName = StringNormalization.blankToNull(judgeName);
        title = StringNormalization.blankToNull(title);
    }

    // title/procedure/instance/status/responsibleUserId/registryWatchEnabled - усі NOT NULL на
    // Case. JsonNullable дозволяє в тілі PATCH-запиту буквально написати "title": null - без цих
    // перевірок таке тіло проходило б валідацію (JsonNullable сам по собі не забороняє
    // "присутнє й null"), і CaseMapper.applyPresentFields чесно виконав би setIfPresent(...,
    // case_::setTitle) - для String/enum-полів це падало б лише пізніше, на Hibernate-флаші
    // ("not-null property references a null value", 500), а для примітивних
    // responsibleUserId/registryWatchEnabled - одразу NPE на автоанбоксингу в сеттер. Тут - явний
    // 400 ще на межі контролера, як і мало бути для невалідного вхідного тіла.
    @JsonIgnore
    @AssertTrue(message = "'title' must not be explicitly set to null")
    public boolean isTitleValidIfPresent() {
        return notExplicitlyNull(title);
    }

    @JsonIgnore
    @AssertTrue(message = "'procedure' must not be explicitly set to null")
    public boolean isProcedureValidIfPresent() {
        return notExplicitlyNull(procedure);
    }

    @JsonIgnore
    @AssertTrue(message = "'instance' must not be explicitly set to null")
    public boolean isInstanceValidIfPresent() {
        return notExplicitlyNull(instance);
    }

    @JsonIgnore
    @AssertTrue(message = "'status' must not be explicitly set to null")
    public boolean isStatusValidIfPresent() {
        return notExplicitlyNull(status);
    }

    @JsonIgnore
    @AssertTrue(message = "'responsibleUserId' must not be explicitly set to null")
    public boolean isResponsibleUserIdValidIfPresent() {
        return notExplicitlyNull(responsibleUserId);
    }

    @JsonIgnore
    @AssertTrue(message = "'registryWatchEnabled' must not be explicitly set to null")
    public boolean isRegistryWatchEnabledValidIfPresent() {
        return notExplicitlyNull(registryWatchEnabled);
    }

    private static boolean notExplicitlyNull(JsonNullable<?> value) {
        return value == null || !value.isPresent() || value.get() != null;
    }
}

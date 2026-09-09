package com.lisovskyi.core_service.deadline.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.openapitools.jackson.nullable.JsonNullable;

import java.time.LocalDate;

// SEN-29 AC3: title/startsOn/dueOn - обов'язкові поля звичайного запису (юрист завжди вводить
// назву й обидві дати сам, нема з чого їх порахувати автоматично). legalBasis/note/
// triggeringEventId - опційні, тому JsonNullable, а не голий nullable-тип: за відсутності поля
// в JSON (undefined) і явного null є однакова поведінка на CREATE (нема різниці "не чіпати" vs
// "очистити", як на PATCH), але JsonNullable зберігає той самий контракт формату запиту, що й
// решта DTO цього модуля (CaseEventUpdateRequest, CaseUpdateRequest).
public record DeadlineManualRegisterRequest(
        @NotBlank String title,
        @NotNull LocalDate startsOn,
        @NotNull LocalDate dueOn,
        JsonNullable<String> legalBasis,
        JsonNullable<String> note,
        JsonNullable<Long> triggeringEventId
) {}

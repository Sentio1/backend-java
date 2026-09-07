package com.lisovskyi.core_service.deadline_rule.dto.request;

import static com.lisovskyi.core_service.deadline_rule.DeadlineRuleConstants.*;

import com.lisovskyi.core_service.case_.enums.ProcedureType;
import com.lisovskyi.core_service.case_event.enums.EventCode;
import com.lisovskyi.core_service.court.CourtInstance;
import com.lisovskyi.core_service.deadline_rule.enums.CountFrom;
import com.lisovskyi.core_service.deadline_rule.enums.DayKind;
import com.lisovskyi.core_service.deadline_rule.enums.DurationUnit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.openapitools.jackson.nullable.JsonNullable;

// Той самий DTO і для першої версії нового правила (code ще не зустрічався - сервіс
// присвоює version = 1), і для наступної версії наявного (code збігається з чинним
// рядком - сервіс закриває validTo попередньої версії й ставить version = попередня +
// 1). Правило "ніколи не редагується на місці" (AC SEN-24) саме тому й не має тут
// суцільних JsonNullable-полів, як DeadlineRuleUpdateRequest: немає операції "змінити
// зміст наявної версії", є лише "додати нову" - а нова версія так само повністю
// визначена, як і перша. DeadlineRuleUpdateRequest лишається для того, що дійсно можна
// правити в наявному рядку без переписування історії - див. коментар там.
//
// id/version свідомо відсутні як поля - їх обчислює сервіс, а не клієнт.
public record DeadlineRuleCreateRequest(
        @NotBlank @Size(max = CODE_LENGTH) String code,

        @NotNull ProcedureType procedure,

        @NotNull CourtInstance instance,

        @NotNull EventCode triggerEventCode,

        @NotBlank @Size(max = TITLE_LENGTH) String title,

        @NotBlank @Size(max = LEGAL_BASIS_LENGTH) String legalBasis,

        @NotNull @Positive Short durationValue,

        // durationUnit/dayKind/countFrom/extendable - навмисно НЕ JsonNullable з падінням
        // на @Builder.Default entity, хоча в БД так само NOT NULL DEFAULT: для довідника,
        // де "юрист має бачити, за яким правилом порахована дата" (SEN-24), мовчазний
        // дефолт (CALENDAR/NEXT_DAY/DAY/false) на пропущеному полі - це саме той
        // прихований вибір, якого issue хоче уникнути. Автор правила вказує їх явно,
        // пропуск поля - це 400, а не тиха підстановка.
        @NotNull DurationUnit durationUnit,

        @NotNull DayKind dayKind,

        @NotNull CountFrom countFrom,

        @NotNull Boolean extendable,

        @NotNull LocalDate validFrom,

        // null = чинне безстроково. JsonNullable, а не голий LocalDate: єдине тут поле,
        // яке справді nullable в БД без прихованого дефолту - той самий випадок, що й
        // Case.courtId/judgeName у CaseCreateRequest.
        JsonNullable<LocalDate> validTo) {}

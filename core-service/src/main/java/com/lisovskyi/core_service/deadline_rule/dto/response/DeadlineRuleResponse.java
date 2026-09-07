package com.lisovskyi.core_service.deadline_rule.dto.response;

import com.lisovskyi.core_service.case_.enums.ProcedureType;
import com.lisovskyi.core_service.case_event.enums.EventCode;
import com.lisovskyi.core_service.court.CourtInstance;
import com.lisovskyi.core_service.deadline_rule.enums.CountFrom;
import com.lisovskyi.core_service.deadline_rule.enums.DayKind;
import com.lisovskyi.core_service.deadline_rule.enums.DurationUnit;
import java.time.LocalDate;

public record DeadlineRuleResponse(
        Long id,
        String code,
        ProcedureType procedure,
        CourtInstance instance,
        EventCode triggerEventCode,
        String title,
        String legalBasis,
        short durationValue,
        DurationUnit durationUnit,
        DayKind dayKind,
        CountFrom countFrom,
        boolean extendable,
        LocalDate validFrom,

        // null = чинне безстроково (та сама семантика, що й на ентіті/запиті).
        LocalDate validTo,

        // Номер редакції - разом із validFrom/validTo дає змогу показати юристу історію
        // версій одного code (GET .../deadline-rules?code=... -> список, відсортований за
        // version), а не лише поточне чинне правило.
        short version) {}

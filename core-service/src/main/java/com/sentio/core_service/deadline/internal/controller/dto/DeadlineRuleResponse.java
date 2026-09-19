package com.sentio.core_service.deadline.internal.controller.dto;

import com.sentio.core_service.litigation.api.enums.ProcedureType;
import com.sentio.core_service.litigation.api.enums.EventCode;
import com.sentio.core_service.court.api.enums.CourtInstance;
import com.sentio.core_service.deadline.internal.enums.CountFrom;
import com.sentio.core_service.deadline.internal.enums.DayKind;
import com.sentio.core_service.deadline.internal.enums.DurationUnit;
import java.time.LocalDate;

public record DeadlineRuleResponse(
        long id,
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

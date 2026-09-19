package com.sentio.core_service.calendar.api.service;

import java.time.LocalDate;
import java.util.Map;

public interface HolidayCalendar {

    /**
     * Days in [from, to] whose working status differs from the plain weekday/weekend rule, as
     * "date -> is it a working day" - the format WorkingDayCalendar expects. asOf: the calendar as
     * known on that date - rows with effectiveFrom after asOf (a transfer announced but not yet in
     * force) are left out.
     */
    Map<LocalDate, Boolean> findWorkingDayOverrides(LocalDate from, LocalDate to, LocalDate asOf);
}

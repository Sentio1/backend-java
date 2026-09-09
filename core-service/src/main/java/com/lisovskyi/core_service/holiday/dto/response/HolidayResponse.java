package com.lisovskyi.core_service.holiday.dto.response;

import com.lisovskyi.core_service.holiday.enums.HolidayType;
import java.time.LocalDate;

// isWorking - похідне від holidayType (Holiday.isWorking()), лишене поруч у відповіді як
// зручний прапорець для споживачів, яким не треба розрізняти PUBLIC_HOLIDAY від
// TRANSFERRED_NON_WORKING_DAY, а треба лише "чи цей день робочий".
public record HolidayResponse(
        LocalDate date,
        String name,
        HolidayType holidayType,
        boolean isWorking,
        short year,
        LocalDate effectiveFrom
) {}

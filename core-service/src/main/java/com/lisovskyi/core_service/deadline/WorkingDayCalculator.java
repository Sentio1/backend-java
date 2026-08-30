package com.lisovskyi.core_service.deadline;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

// Чиста арифметика для гілки DAY + WORKING у deadlineEngine (CaseEventService) — без
// залежності від репозиторіїв/Spring, щоб покривати юніт-тестами без Testcontainers.
public final class WorkingDayCalculator {

    private WorkingDayCalculator() {
        throw new UnsupportedOperationException();
    }

    public static LocalDate calculateDueOn(
            LocalDate startsOn,
            short workingDaysCount,
            LocalDate upperBoundDate,
            Map<LocalDate, Boolean> holidayOverrides) {
        int count = 0;
        for (LocalDate date = startsOn.plusDays(1); !date.isAfter(upperBoundDate); date = date.plusDays(1)) {
            if (isWorkingDay(date, holidayOverrides)) {
                count++;
                if (count == workingDaysCount) {
                    return date;
                }
            }
        }

        throw new IllegalStateException("Could not compute dueOn within window: startsOn=" + startsOn
                + ", upperBound=" + upperBoundDate
                + ", required working days=" + workingDaysCount);
    }

    private static boolean isWorkingDay(LocalDate date, Map<LocalDate, Boolean> holidayOverrides) {
        Boolean override = holidayOverrides.get(date);
        if (override != null) {
            return override;
        }

        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }
}

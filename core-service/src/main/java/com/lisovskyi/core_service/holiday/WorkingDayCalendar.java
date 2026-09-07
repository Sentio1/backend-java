package com.lisovskyi.core_service.holiday;

import org.jspecify.annotations.NonNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

// SEN-26: "чи день робочий" / "наступний робочий день" / "додати N робочих днів" — над уже
// матеріалізованою мапою "дата -> чи цей день робочий" (override для свят і перенесень;
// усе поза мапою — звичайний вихідний/будній за DayOfWeek). Чиста функція, без Spring і без
// БД - матеріалізацію цієї мапи з core.holidays (з урахуванням effective_from) робить виклик,
// що дістає дані, а не цей клас. deadline_processor.DeadlineCalculator (SEN-27) використовує
// isWorkingDay/nextWorkingDayOnOrAfter як свою єдину точку правди про "робочий день", а власний
// проміняний рахунок робочих днів веде сам - там ще й зупинення (SuspensionPeriod), яке до
// календаря як такого не належить.
public final class WorkingDayCalendar {

    private WorkingDayCalendar() {
        throw new UnsupportedOperationException();
    }

    public static boolean isWorkingDay(@NonNull LocalDate date, Map<LocalDate, Boolean> holidayOverrides) {
        Boolean override = holidayOverrides.get(date);
        if (override != null) {
            return override;
        }

        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }

    // Сама дата, якщо вона вже робоча, інакше - найближчий робочий день ПІСЛЯ неї. Саме ця
    // ("on or after", а не "строго наступний") семантика потрібна для перенесення закінчення
    // строку з вихідного/свята на робочий день.
    public static LocalDate nextWorkingDayOnOrAfter(LocalDate date, Map<LocalDate, Boolean> holidayOverrides) {
        LocalDate result = date;
        while (!isWorkingDay(result, holidayOverrides)) {
            result = result.plusDays(1);
        }
        return result;
    }

    // Строго наступний робочий день ПІСЛЯ date (не враховує date).
    public static LocalDate nextWorkingDayAfter(LocalDate date, Map<LocalDate, Boolean> holidayOverrides) {
        LocalDate result = date.plusDays(1);
        while (!isWorkingDay(result, holidayOverrides)) {
            result = result.plusDays(1);
        }
        return result;
    }

    // Дата за count робочих днів ПІСЛЯ from (сам from не рахується, навіть якщо він робочий).
    public static LocalDate addWorkingDays(@NonNull LocalDate from, int count, Map<LocalDate, Boolean> holidayOverrides) {
        LocalDate maxSearchDate = from.plusDays(searchWindowDays(count));

        int found = 0;
        for (LocalDate date = from.plusDays(1); !date.isAfter(maxSearchDate); date = date.plusDays(1)) {
            if (isWorkingDay(date, holidayOverrides)) {
                found++;
                if (found == count) {
                    return date;
                }
            }
        }

        throw new IllegalStateException(
                "Could not add " + count + " working day(s) to " + from + " within window up to " + maxSearchDate);
    }

    // Щедрий запас на пошук: до 7 календарних днів на кожен робочий (з великим запасом на
    // свята) + місяць буфера.
    private static long searchWindowDays(int count) {
        return count * 7L + 30L;
    }
}

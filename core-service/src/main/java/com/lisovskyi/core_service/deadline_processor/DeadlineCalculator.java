package com.lisovskyi.core_service.deadline_processor;

import com.lisovskyi.core_service.deadline_rule.enums.CountFrom;
import com.lisovskyi.core_service.deadline_rule.enums.DayKind;
import com.lisovskyi.core_service.deadline_rule.enums.DurationUnit;
import org.jspecify.annotations.NonNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

// SEN-27: подія + правило + календар → дата. Чиста арифметика java.time — без Spring,
// без репозиторіїв, без Date/Calendar/SimpleDateFormat. Усе, що рушію (DeadlineEngine) треба
// знати з БД (свята, зупинення), він дістає сам і передає сюди аргументами.
public final class DeadlineCalculator {

    private DeadlineCalculator() {
        throw new UnsupportedOperationException();
    }

    // строк тече з наступного дня після події (NEXT_DAY) або з дня самої події (SAME_DAY).
    public static LocalDate calculateStartsOn(@NonNull LocalDate eventDate, @NonNull CountFrom countFrom) {
        return countFrom == CountFrom.NEXT_DAY ? eventDate.plusDays(1) : eventDate;
    }

    public static LocalDate calculateDueOn(
            @NonNull LocalDate startsOn,
            @NonNull DurationUnit durationUnit,
            @NonNull DayKind dayKind,
            short durationValue,
            Map<LocalDate, Boolean> holidayOverrides
    ) {
        return calculateDueOn(startsOn, durationUnit, dayKind, durationValue, holidayOverrides, List.of());
    }

    public static LocalDate calculateDueOn(
            @NonNull LocalDate startsOn,
            @NonNull DurationUnit durationUnit,
            @NonNull DayKind dayKind,
            short durationValue,
            Map<LocalDate, Boolean> holidayOverrides,
            List<SuspensionPeriod> suspensions
    ) {

        // Робочі дні: свята й вихідні не рахуються за визначенням — окрема гілка, що йде
        // day-by-day, а не рахує "наперед" і переносить, як календарні.
        if (durationUnit == DurationUnit.DAY && dayKind == DayKind.WORKING) {
            return advanceByWorkingDays(startsOn, durationValue, holidayOverrides, suspensions);
        }

        // Календарні дні й місяці — рахуємо "наперед" арифметикою java.time, продовжуємо на
        // час зупинення (якщо було), і якщо результат впав на вихідний/свято — переносимо
        // на найближчий робочий день.
        LocalDate naiveDueOn = durationUnit == DurationUnit.MONTH
                ? startsOn.plusMonths(durationValue)
                : startsOn.plusDays(durationValue);

        LocalDate extendedDueOn = extendForSuspensions(startsOn, naiveDueOn, suspensions);
        return postponeIfNonWorking(extendedDueOn, holidayOverrides);
    }

    private static LocalDate advanceByWorkingDays(
            @NonNull LocalDate startsOn,
            short workingDaysCount,
            Map<LocalDate, Boolean> holidayOverrides,
            List<SuspensionPeriod> suspensions) {
        LocalDate maxSearchDate = startsOn.plusDays(searchWindowDays(workingDaysCount, suspensions));

        int found = 0;
        for (LocalDate date = startsOn.plusDays(1); !date.isAfter(maxSearchDate); date = date.plusDays(1)) {
            if (isSuspended(date, suspensions)) {
                continue;
            }
            if (isWorkingDay(date, holidayOverrides)) {
                found++;
                if (found == workingDaysCount) {
                    return date;
                }
            }
        }

        throw new IllegalStateException("Could not compute dueOn within window: startsOn=" + startsOn
                + ", upperBound=" + maxSearchDate
                + ", required working days=" + workingDaysCount);
    }

    // Запас на пошук: до 7 календарних днів на кожен робочий (з великим запасом на свята) +
    // сумарна тривалість усіх періодів зупинення + місяць буфера.
    private static long searchWindowDays(short workingDaysCount, List<SuspensionPeriod> suspensions) {
        return workingDaysCount * 7L + totalSuspendedDays(suspensions) + 30L;
    }

    private static long totalSuspendedDays(List<SuspensionPeriod> suspensions) {
        return suspensions.stream()
                .mapToLong(period -> ChronoUnit.DAYS.between(period.from(), period.to()) + 1)
                .sum();
    }

    // Продовжує наївну (без урахування зупинень) дату на кількість днів зупинення, що
    // потрапляють у [startsOn, dueOn). Дата тільки росте, а сумарна тривалість зупинень
    // скінченна — тож цикл завжди сходиться, без штучного ліміту ітерацій.
    private static LocalDate extendForSuspensions(
            @NonNull LocalDate startsOn, LocalDate naiveDueOn, List<SuspensionPeriod> suspensions) {
        if (suspensions.isEmpty()) {
            return naiveDueOn;
        }

        LocalDate dueOn = naiveDueOn;
        LocalDate previousDueOn;
        do {
            previousDueOn = dueOn;
            long suspendedDays = countSuspendedDays(startsOn, dueOn, suspensions);
            dueOn = naiveDueOn.plusDays(suspendedDays);
        } while (!dueOn.equals(previousDueOn));

        return dueOn;
    }

    private static long countSuspendedDays(LocalDate startsOn, LocalDate dueOn, List<SuspensionPeriod> suspensions) {
        return suspensions.stream()
                .mapToLong(period -> overlapDays(startsOn, dueOn, period))
                .sum();
    }

    // Дні періоду зупинення, що потрапляють у [from, to) — сам to (це вже розрахована дата,
    // а не день перебігу строку) до уваги не береться.
    private static long overlapDays(@NonNull LocalDate from, @NonNull LocalDate to,@NonNull SuspensionPeriod period) {
        LocalDate overlapFrom = max(period.from(), from);
        LocalDate overlapTo = min(period.to(), to.minusDays(1));
        return overlapTo.isBefore(overlapFrom) ? 0 : ChronoUnit.DAYS.between(overlapFrom, overlapTo) + 1;
    }

    private static LocalDate postponeIfNonWorking(@NonNull LocalDate date, Map<LocalDate, Boolean> holidayOverrides) {
        LocalDate result = date;
        while (!isWorkingDay(result, holidayOverrides)) {
            result = result.plusDays(1);
        }
        return result;
    }

    private static boolean isSuspended(@NonNull LocalDate date, List<SuspensionPeriod> suspensions) {
        return suspensions.stream().anyMatch(period -> period.contains(date));
    }

    private static boolean isWorkingDay(@NonNull LocalDate date, Map<LocalDate, Boolean> holidayOverrides) {
        Boolean override = holidayOverrides.get(date);
        if (override != null) {
            return override;
        }

        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }

    private static LocalDate max(@NonNull LocalDate a, @NonNull LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate min(@NonNull LocalDate a, @NonNull LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}

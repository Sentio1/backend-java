package com.lisovskyi.core_service.deadline_processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.lisovskyi.core_service.deadline_processor.dto.DeadlineDatesResponse;
import com.lisovskyi.core_service.deadline_rule.enums.CountFrom;
import com.lisovskyi.core_service.deadline_rule.enums.DayKind;
import com.lisovskyi.core_service.deadline_rule.enums.DurationUnit;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * SEN-27: подія + правило + календар → дата, чистою функцією {@link DeadlineCalculator} - без
 * Spring, без БД. Основний масив кейсів - таблицею (подія → очікувана дата), а не окремими
 * @Test-методами, щоб знайдену юристом-консультантом помилку можна було додати одним рядком.
 */
class DeadlineCalculatorTest {

    // ─── подія + правило → startsOn/dueOn, без свят і без зупинень ────────
    //
    // event      | countFrom | unit  | dayKind  | value | startsOn   | dueOn
    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                    2024-06-03, NEXT_DAY, DAY,   CALENDAR, 5, 2024-06-04, 2024-06-10
                    2024-06-05, SAME_DAY, DAY,   CALENDAR, 2, 2024-06-05, 2024-06-07
                    2023-12-25, SAME_DAY, DAY,   CALENDAR, 6, 2023-12-25, 2024-01-01
                    2024-01-30, NEXT_DAY, MONTH, CALENDAR, 1, 2024-01-31, 2024-02-29
                    2023-01-30, NEXT_DAY, MONTH, CALENDAR, 1, 2023-01-31, 2023-02-28
                    2024-06-08, NEXT_DAY, DAY,   CALENDAR, 5, 2024-06-09, 2024-06-14
                    2024-06-07, NEXT_DAY, DAY,   WORKING,  1, 2024-06-08, 2024-06-10
                    2024-06-03, NEXT_DAY, DAY,   WORKING,  5, 2024-06-04, 2024-06-11
                    2024-05-07, NEXT_DAY, MONTH, CALENDAR, 1, 2024-05-08, 2024-06-10
                    """)
    void calculate(
            LocalDate eventDate,
            CountFrom countFrom,
            DurationUnit durationUnit,
            DayKind dayKind,
            short durationValue,
            LocalDate expectedStartsOn,
            LocalDate expectedDueOn) {
        LocalDate startsOn = DeadlineCalculator.calculateStartsOn(eventDate, countFrom);
        DeadlineDatesResponse dates =
                DeadlineCalculator.calculateDueOn(startsOn, durationUnit, dayKind, durationValue, Map.of());

        assertThat(startsOn).isEqualTo(expectedStartsOn);
        assertThat(dates.dueOn()).isEqualTo(expectedDueOn);
    }

    // ─── свята (не лише вихідні) ────────────────────────────────────────

    private static final LocalDate MONDAY = LocalDate.of(2024, 6, 3);
    private static final LocalDate FRIDAY = LocalDate.of(2024, 6, 7);

    @ParameterizedTest
    @MethodSource("holidayWorkingDayCases")
    void calculateDueOn_workingDays_respectsHolidayOverrides(
            LocalDate startsOn, short durationValue, Map<LocalDate, Boolean> holidayOverrides, LocalDate expectedDueOn) {
        DeadlineDatesResponse dates = DeadlineCalculator.calculateDueOn(
                startsOn, DurationUnit.DAY, DayKind.WORKING, durationValue, holidayOverrides);

        assertThat(dates.dueOn()).isEqualTo(expectedDueOn);
        // Робочі дні: результат апріорі впадає на робочий день, тут нема окремого кроку
        // "перенести" - naiveDueOn і dueOn завжди збігаються (SEN-28).
        assertThat(dates.naiveDueOn()).isEqualTo(expectedDueOn);
    }

    private static Stream<Arguments> holidayWorkingDayCases() {
        return Stream.of(
                Arguments.of(
                        MONDAY, (short) 1, Map.of(LocalDate.of(2024, 6, 4), false), LocalDate.of(2024, 6, 5)),
                Arguments.of(
                        FRIDAY, (short) 2, Map.of(LocalDate.of(2024, 6, 8), true), LocalDate.of(2024, 6, 10)));
    }

    @org.junit.jupiter.api.Test
    void calculateDueOn_calendarDays_postponesFromAnOfficialHoliday_notJustAWeekend() {
        LocalDate startsOn = LocalDate.of(2024, 6, 3);
        Map<LocalDate, Boolean> holidays = Map.of(LocalDate.of(2024, 6, 10), false);

        DeadlineDatesResponse dates =
                DeadlineCalculator.calculateDueOn(startsOn, DurationUnit.DAY, DayKind.CALENDAR, (short) 6, holidays);

        // Наївно (без перенесення) 03.06 (пн) + 6 календарних днів = 09.06 (неділя); і 09.06,
        // і оголошений неробочим 10.06 пропускаються - результат 11.06. naiveDueOn зберігає
        // саме "до перенесення" (09.06), щоб SEN-28 міг пояснити "перенесено з неділі на 11.06".
        assertThat(dates.naiveDueOn()).isEqualTo(LocalDate.of(2024, 6, 9));
        assertThat(dates.dueOn()).isEqualTo(LocalDate.of(2024, 6, 11));
    }

    // ─── зупинення й поновлення перебігу строку ────────────────────────

    @org.junit.jupiter.api.Test
    void calculateDueOn_calendarDays_extendsBySuspensionOverlappingTheRun() {
        LocalDate startsOn = LocalDate.of(2024, 6, 3);
        List<SuspensionPeriod> suspensions =
                List.of(new SuspensionPeriod(LocalDate.of(2024, 6, 5), LocalDate.of(2024, 6, 6)));

        // Наївно (без зупинення) 5 календарних днів від 03.06 = 08.06, +2 дні зупинення = 10.06
        // (понеділок, робочий - подальшого перенесення календарем нема, naiveDueOn == dueOn).
        DeadlineDatesResponse dates = DeadlineCalculator.calculateDueOn(
                startsOn, DurationUnit.DAY, DayKind.CALENDAR, (short) 5, Map.of(), suspensions);

        assertThat(dates.naiveDueOn()).isEqualTo(LocalDate.of(2024, 6, 10));
        assertThat(dates.dueOn()).isEqualTo(LocalDate.of(2024, 6, 10));
    }

    @org.junit.jupiter.api.Test
    void calculateDueOn_extensionCanExposeALaterSuspensionPeriod_convergesInMoreThanOneStep() {
        // 10 календарних днів від 01.01.2024 (пн) = 11.01 наївно. Перше зупинення (05-06.01,
        // 2 дні) зсуває на 13.01 - і це нове вікно вже накриває друге зупинення (12.01, 1 день),
        // якого в наївному вікні не було. Підсумково 11.01 + 3 = 14.01 (неділя) → перенесення на
        // понеділок 15.01. Без другої ітерації (наївний однопрохідний підрахунок) вийшло б 13.01.
        LocalDate startsOn = LocalDate.of(2024, 1, 1);
        List<SuspensionPeriod> suspensions = List.of(
                new SuspensionPeriod(LocalDate.of(2024, 1, 5), LocalDate.of(2024, 1, 6)),
                new SuspensionPeriod(LocalDate.of(2024, 1, 12), LocalDate.of(2024, 1, 12)));

        DeadlineDatesResponse dates = DeadlineCalculator.calculateDueOn(
                startsOn, DurationUnit.DAY, DayKind.CALENDAR, (short) 10, Map.of(), suspensions);

        // extendedDueOn (14.01, неділя) - те, що фактично пішло на перенесення календарем,
        // а не "сира" 11.01 без урахування зупинень.
        assertThat(dates.naiveDueOn()).isEqualTo(LocalDate.of(2024, 1, 14));
        assertThat(dates.dueOn()).isEqualTo(LocalDate.of(2024, 1, 15));
    }

    @org.junit.jupiter.api.Test
    void calculateDueOn_suspensionAfterTheNaiveDueDate_doesNotExtendAnything() {
        LocalDate startsOn = LocalDate.of(2024, 6, 3);
        List<SuspensionPeriod> suspensions =
                List.of(new SuspensionPeriod(LocalDate.of(2024, 6, 20), LocalDate.of(2024, 6, 25)));

        DeadlineDatesResponse dates = DeadlineCalculator.calculateDueOn(
                startsOn, DurationUnit.DAY, DayKind.CALENDAR, (short) 5, Map.of(), suspensions);

        // Наївно (без зупинення) 03.06 (пн) + 5 календарних днів = 08.06 (субота) - зупинення
        // після цієї дати жодного дня не додає, лишається тільки перенесення з вихідного на 10.06.
        assertThat(dates.naiveDueOn()).isEqualTo(LocalDate.of(2024, 6, 8));
        assertThat(dates.dueOn()).isEqualTo(LocalDate.of(2024, 6, 10));
    }

    @org.junit.jupiter.api.Test
    void calculateDueOn_workingDays_skipsSuspendedWeekdays_inAdditionToWeekendsAndHolidays() {
        LocalDate startsOn = LocalDate.of(2024, 6, 3); // понеділок
        List<SuspensionPeriod> suspensions =
                List.of(new SuspensionPeriod(LocalDate.of(2024, 6, 4), LocalDate.of(2024, 6, 5)));

        // вт/ср зупинені (не рахуються), чт(1)/пт(2)/сб-нд(вихідні, скіп)/пн(3).
        DeadlineDatesResponse dates = DeadlineCalculator.calculateDueOn(
                startsOn, DurationUnit.DAY, DayKind.WORKING, (short) 3, Map.of(), suspensions);

        assertThat(dates.dueOn()).isEqualTo(LocalDate.of(2024, 6, 10));
        assertThat(dates.naiveDueOn()).isEqualTo(LocalDate.of(2024, 6, 10));
    }

    // ─── межові/захисні випадки ─────────────────────────────────────────

    @org.junit.jupiter.api.Test
    void calculateDueOn_workingDays_whenEveryDayInSearchWindowIsAHoliday_throwsIllegalStateException_insteadOfSilentlyGuessing() {
        LocalDate startsOn = LocalDate.of(2024, 6, 3);
        Map<LocalDate, Boolean> allHolidays = startsOn.plusDays(1)
                .datesUntil(startsOn.plusDays(100))
                .collect(java.util.stream.Collectors.toMap(date -> date, date -> false));

        assertThatIllegalStateException()
                .isThrownBy(() -> DeadlineCalculator.calculateDueOn(
                        startsOn, DurationUnit.DAY, DayKind.WORKING, (short) 1, allHolidays))
                .withMessageContaining("startsOn=" + startsOn);
    }

    @org.junit.jupiter.api.Test
    void suspensionPeriod_withToBeforeFrom_throwsIllegalArgumentException() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SuspensionPeriod(LocalDate.of(2024, 6, 10), LocalDate.of(2024, 6, 1)));
    }
}

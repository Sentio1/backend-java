package com.lisovskyi.core_service.deadline;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class WorkingDayCalculatorTest {

    private static final LocalDate MONDAY = LocalDate.of(2024, 6, 3);
    private static final LocalDate FRIDAY = LocalDate.of(2024, 6, 7);

    @Test
    void oneWorkingDay_fromMonday_withNoHolidays_landsOnTuesday() {
        LocalDate dueOn =
                WorkingDayCalculator.calculateDueOn(MONDAY, (short) 1, MONDAY.plusDays(30), Map.of());

        assertThat(dueOn).isEqualTo(LocalDate.of(2024, 6, 4));
    }

    @Test
    void startsOnItself_isNeverCounted_evenWhenItIsAWorkingDay() {
        // Узгодженість із CALENDAR-гілкою (startsOn.plusDays(N)): startsOn — точка відліку,
        // а не перший з N днів, навіть якщо сам він робочий.
        LocalDate dueOn =
                WorkingDayCalculator.calculateDueOn(MONDAY, (short) 1, MONDAY.plusDays(30), Map.of());

        assertThat(dueOn).isNotEqualTo(MONDAY);
    }

    @Test
    void weekend_isSkipped_byDefault() {
        // П'ятниця + 1 робочий день: субота й неділя не рахуються, тож дедлайн — понеділок.
        LocalDate dueOn =
                WorkingDayCalculator.calculateDueOn(FRIDAY, (short) 1, FRIDAY.plusDays(30), Map.of());

        assertThat(dueOn).isEqualTo(LocalDate.of(2024, 6, 10));
    }

    @Test
    void officialHoliday_onAWeekday_isSkipped() {
        // Вівторок (04.06) — офіційне свято (isWorking=false), хоч і будній день.
        Map<LocalDate, Boolean> holidays = Map.of(LocalDate.of(2024, 6, 4), false);

        LocalDate dueOn =
                WorkingDayCalculator.calculateDueOn(MONDAY, (short) 1, MONDAY.plusDays(30), holidays);

        assertThat(dueOn).isEqualTo(LocalDate.of(2024, 6, 5));
    }

    @Test
    void movedWorkingDay_onAWeekend_countsAsWorking() {
        // Субота (08.06) — перенесений робочий день (isWorking=true). Друга неділя лишається
        // вихідною за замовчуванням.
        Map<LocalDate, Boolean> holidays = Map.of(LocalDate.of(2024, 6, 8), true);

        LocalDate dueOn =
                WorkingDayCalculator.calculateDueOn(FRIDAY, (short) 2, FRIDAY.plusDays(30), holidays);

        assertThat(dueOn).isEqualTo(LocalDate.of(2024, 6, 10));
    }

    @Test
    void windowTooSmall_throwsIllegalStateException_insteadOfSilentlyUndercounting() {
        assertThatIllegalStateException()
                .isThrownBy(() ->
                        WorkingDayCalculator.calculateDueOn(MONDAY, (short) 100, MONDAY.plusDays(5), Map.of()))
                .withMessageContaining("startsOn=" + MONDAY);
    }
}

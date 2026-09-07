package com.lisovskyi.core_service.holiday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * SEN-26: календарна арифметика (робочий день / найближчий робочий день / +N робочих днів) над
 * мапою override'ів свят — винесена окремо від {@link
 * com.lisovskyi.core_service.deadline_processor.DeadlineCalculator} (SEN-27), який
 * використовує саме цей клас як єдину точку правди про "робочий день".
 */
class WorkingDayCalendarTest {

    private static final LocalDate MONDAY = LocalDate.of(2024, 6, 3);
    private static final LocalDate SATURDAY = LocalDate.of(2024, 6, 8);
    private static final LocalDate SUNDAY = LocalDate.of(2024, 6, 9);

    @Test
    void isWorkingDay_weekday_withNoOverride_isTrue() {
        assertThat(WorkingDayCalendar.isWorkingDay(MONDAY, Map.of())).isTrue();
    }

    @Test
    void isWorkingDay_weekend_withNoOverride_isFalse() {
        assertThat(WorkingDayCalendar.isWorkingDay(SATURDAY, Map.of())).isFalse();
        assertThat(WorkingDayCalendar.isWorkingDay(SUNDAY, Map.of())).isFalse();
    }

    @Test
    void isWorkingDay_officialHoliday_onAWeekday_isFalse() {
        Map<LocalDate, Boolean> holidays = Map.of(MONDAY, false);

        assertThat(WorkingDayCalendar.isWorkingDay(MONDAY, holidays)).isFalse();
    }

    @Test
    void isWorkingDay_transferredWorkingDay_onAWeekend_isTrue() {
        Map<LocalDate, Boolean> holidays = Map.of(SATURDAY, true);

        assertThat(WorkingDayCalendar.isWorkingDay(SATURDAY, holidays)).isTrue();
    }

    @Test
    void nextWorkingDayOnOrAfter_alreadyWorking_returnsTheSameDate() {
        assertThat(WorkingDayCalendar.nextWorkingDayOnOrAfter(MONDAY, Map.of())).isEqualTo(MONDAY);
    }

    @Test
    void nextWorkingDayOnOrAfter_weekend_rollsForwardToMonday() {
        assertThat(WorkingDayCalendar.nextWorkingDayOnOrAfter(SATURDAY, Map.of()))
                .isEqualTo(LocalDate.of(2024, 6, 10));
    }

    @Test
    void nextWorkingDayOnOrAfter_officialHoliday_rollsForward_notJustPastWeekends() {
        Map<LocalDate, Boolean> holidays = Map.of(MONDAY, false);

        assertThat(WorkingDayCalendar.nextWorkingDayOnOrAfter(MONDAY, holidays))
                .isEqualTo(LocalDate.of(2024, 6, 4));
    }

    @Test
    void addWorkingDays_skipsWeekend() {
        // П'ятниця + 1 робочий день: субота й неділя не рахуються, тож понеділок.
        LocalDate friday = LocalDate.of(2024, 6, 7);

        assertThat(WorkingDayCalendar.addWorkingDays(friday, 1, Map.of()))
                .isEqualTo(LocalDate.of(2024, 6, 10));
    }

    @Test
    void addWorkingDays_fromItself_neverCountsTheStartDate_evenWhenItIsWorking() {
        assertThat(WorkingDayCalendar.addWorkingDays(MONDAY, 1, Map.of())).isNotEqualTo(MONDAY);
    }

    @Test
    void addWorkingDays_transferredWorkingDay_countsTowardsTheTotal() {
        // Субота (08.06) - перенесений робочий день: 2 робочі дні з п'ятниці - субота (1) і
        // понеділок (2), а не вівторок.
        LocalDate friday = LocalDate.of(2024, 6, 7);
        Map<LocalDate, Boolean> holidays = Map.of(SATURDAY, true);

        assertThat(WorkingDayCalendar.addWorkingDays(friday, 2, holidays)).isEqualTo(LocalDate.of(2024, 6, 10));
    }

    @Test
    void addWorkingDays_windowTooSmall_throwsIllegalStateException_insteadOfSilentlyUndercounting() {
        Map<LocalDate, Boolean> allHolidays = MONDAY.plusDays(1)
                .datesUntil(MONDAY.plusDays(1000))
                .collect(java.util.stream.Collectors.toMap(date -> date, date -> false));

        assertThatIllegalStateException()
                .isThrownBy(() -> WorkingDayCalendar.addWorkingDays(MONDAY, 1, allHolidays))
                .withMessageContaining("to " + MONDAY);
    }
}

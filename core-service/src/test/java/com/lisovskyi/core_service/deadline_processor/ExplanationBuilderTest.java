package com.lisovskyi.core_service.deadline_processor;

import static org.assertj.core.api.Assertions.assertThat;

import com.lisovskyi.core_service.case_event.enums.EventCode;
import com.lisovskyi.core_service.deadline_rule.enums.DayKind;
import com.lisovskyi.core_service.deadline_rule.enums.DurationUnit;
import java.time.LocalDate;
import java.util.EnumSet;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * SEN-28: заморожені факти → пояснення розрахунку, чистою функцією {@link ExplanationBuilder} -
 * без Spring, без БД (та сама ідея, що й у {@link DeadlineCalculatorTest} для DeadlineCalculator).
 */
class ExplanationBuilderTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2024, 6, 3);

    @org.junit.jupiter.api.Test
    void build_calendarDays_noShift_hasNoPostponementClause() {
        LocalDate dueOn = LocalDate.of(2024, 6, 8); // субота, але тут вона й є naiveDueOn - без переносу
        String explanation = ExplanationBuilder.build(
                (short) 5, DurationUnit.DAY, DayKind.CALENDAR, BASE_DATE, "ст. 178 ЦПК України", dueOn, dueOn, null);

        assertThat(explanation)
                .isEqualTo("5 календарних днів від 03.06.2024, ст. 178 ЦПК України")
                .doesNotContain("перенесено");
    }

    @org.junit.jupiter.api.Test
    void build_calendarDays_postponedFromSunday_matchesAcWording() {
        LocalDate naiveDueOn = LocalDate.of(2024, 6, 9); // неділя
        LocalDate dueOn = LocalDate.of(2024, 6, 10); // понеділок

        String explanation = ExplanationBuilder.build(
                (short) 6, DurationUnit.DAY, DayKind.CALENDAR, BASE_DATE, "ст. 354 ЦПК", naiveDueOn, dueOn,
                EventCode.COPY_SERVED);

        assertThat(explanation)
                .isEqualTo("6 календарних днів від вручення копії 03.06.2024, ст. 354 ЦПК, "
                        + "закінчення перенесено з неділі на 10.06.2024");
    }

    @org.junit.jupiter.api.Test
    void build_calendarDays_postponedFromSaturday_usesGenitiveOfSaturday() {
        LocalDate naiveDueOn = LocalDate.of(2024, 6, 8); // субота
        LocalDate dueOn = LocalDate.of(2024, 6, 10); // понеділок (свято в неділю на додачу)

        String explanation = ExplanationBuilder.build(
                (short) 5, DurationUnit.DAY, DayKind.CALENDAR, BASE_DATE, null, naiveDueOn, dueOn, null);

        assertThat(explanation).isEqualTo("5 календарних днів від 03.06.2024, закінчення перенесено з суботи на 10.06.2024");
    }

    @org.junit.jupiter.api.Test
    void build_withoutLegalBasis_omitsThatClauseButKeepsPostponement() {
        LocalDate naiveDueOn = LocalDate.of(2024, 6, 9);
        LocalDate dueOn = LocalDate.of(2024, 6, 10);

        String blank = ExplanationBuilder.build(
                (short) 6, DurationUnit.DAY, DayKind.CALENDAR, BASE_DATE, "  ", naiveDueOn, dueOn, null);
        String nullBasis = ExplanationBuilder.build(
                (short) 6, DurationUnit.DAY, DayKind.CALENDAR, BASE_DATE, null, naiveDueOn, dueOn, null);

        assertThat(blank).isEqualTo(nullBasis)
                .isEqualTo("6 календарних днів від 03.06.2024, закінчення перенесено з неділі на 10.06.2024");
    }

    @org.junit.jupiter.api.Test
    void build_workingDays_neverHasPostponementClause() {
        // За контрактом DeadlineCalculator для DayKind.WORKING naiveDueOn і dueOn завжди рівні -
        // окремого кроку "перенести" там нема (рахунок з самого початку йде по робочих днях).
        LocalDate dueOn = LocalDate.of(2024, 6, 11);

        String explanation = ExplanationBuilder.build(
                (short) 3, DurationUnit.DAY, DayKind.WORKING, BASE_DATE, "ст. 178 ЦПК України", dueOn, dueOn, null);

        assertThat(explanation)
                .isEqualTo("3 робочих дні від 03.06.2024, ст. 178 ЦПК України")
                .doesNotContain("перенесено");
    }

    // ─── підпис базової події (SEN-28, варіант 2 - контрольований словник за EventCode) ───

    @org.junit.jupiter.api.Test
    void build_withoutTriggerEventCode_omitsEventLabel_fallsBackToDateOnly() {
        String explanation = ExplanationBuilder.build(
                (short) 5, DurationUnit.DAY, DayKind.CALENDAR, BASE_DATE, null, BASE_DATE.plusDays(5),
                BASE_DATE.plusDays(5), null);

        assertThat(explanation).isEqualTo("5 календарних днів від 03.06.2024");
    }

    // Вичерпний перелік: якщо в EventCode з'явиться нове значення, ExplanationBuilder.eventLabel
    // не скомпілюється, доки для нього не додадуть гілку (switch без default) - цей тест ловить
    // саме випадок "гілку додали, але з порожнім/сміттєвим текстом", що компілятор не бачить.
    @ParameterizedTest
    @EnumSource(EventCode.class)
    void build_everyEventCode_hasANonBlankLabel(EventCode eventCode) {
        String explanation = ExplanationBuilder.build(
                (short) 5, DurationUnit.DAY, DayKind.CALENDAR, BASE_DATE, null, BASE_DATE.plusDays(5),
                BASE_DATE.plusDays(5), eventCode);

        assertThat(explanation).matches("5 календарних днів від \\S.*\\S 03\\.06\\.2024");
    }

    @org.junit.jupiter.api.Test
    void build_allEventCodesCovered_sanityCheckOnEnumSize() {
        // Не про саму логіку - лише нагадування, якщо хтось розширить EventCode: тест вище
        // (EnumSource) підхопить нове значення автоматично й нічого руками оновлювати тут не
        // треба, окрім самого eventLabel у ExplanationBuilder.
        assertThat(EnumSet.allOf(EventCode.class)).hasSize(6);
    }

    // ─── українська плюралізація ("1 день" / "2 дні" / "5 днів" / "11 днів" / "21 день") ──

    @ParameterizedTest
    @CsvSource({"1, день", "2, дні", "4, дні", "5, днів", "10, днів", "11, днів", "14, днів", "21, день", "22, дні"})
    void build_pluralizesDayCountCorrectly(short value, String expectedWord) {
        LocalDate dueOn = BASE_DATE.plusDays(value);
        String explanation = ExplanationBuilder.build(
                value, DurationUnit.DAY, DayKind.CALENDAR, BASE_DATE, null, dueOn, dueOn, null);

        assertThat(explanation).startsWith(value + " календарних " + expectedWord + " від ");
    }

    @ParameterizedTest
    @CsvSource({"1, місяць", "2, місяці", "5, місяців", "11, місяців", "21, місяць"})
    void build_pluralizesMonthCountCorrectly(short value, String expectedWord) {
        LocalDate dueOn = BASE_DATE.plusMonths(value);
        String explanation = ExplanationBuilder.build(
                value, DurationUnit.MONTH, DayKind.CALENDAR, BASE_DATE, null, dueOn, dueOn, null);

        assertThat(explanation).startsWith(value + " " + expectedWord + " від ");
    }
}

package com.lisovskyi.core_service.deadline_processor;

import com.lisovskyi.core_service.case_event.enums.EventCode;
import com.lisovskyi.core_service.deadline_rule.enums.DayKind;
import com.lisovskyi.core_service.deadline_rule.enums.DurationUnit;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.jspecify.annotations.NonNull;

// SEN-28: заморожені на Deadline факти → людське пояснення розрахунку одним реченням.
// Чиста функція за тим самим принципом, що й DeadlineCalculator (SEN-27) - без Spring, без
// репозиторіїв, і зокрема без живого читання DeadlineRule: той рядок можна відредагувати на
// місці через DeadlineRuleService.updateDeadlineRule (без нової версії), тож "жива"
// durationValue/durationUnit/dayKind могли б розійтися з тим, що реально застосувалось при
// розрахунку - сюди приймаються лише вже заморожені на Deadline значення, аргументами.
public final class ExplanationBuilder {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    // Родовий відмінок ("перенесено З ЧОГО") - саме форма з прикладу АС ("з неділі").
    private static final Map<DayOfWeek, String> WEEKDAY_GENITIVE = Map.of(
            DayOfWeek.MONDAY, "понеділка",
            DayOfWeek.TUESDAY, "вівторка",
            DayOfWeek.WEDNESDAY, "середи",
            DayOfWeek.THURSDAY, "четверга",
            DayOfWeek.FRIDAY, "п'ятниці",
            DayOfWeek.SATURDAY, "суботи",
            DayOfWeek.SUNDAY, "неділі");

    private ExplanationBuilder() {
        throw new UnsupportedOperationException();
    }

    // legalBasis - опційний (на Deadline nullable, на відміну від DeadlineRule).
    // triggerEventCode - опційний: triggering_event_id у deadlines nullable (V10), і на
    // старих дедлайнах його могло не бути. EventCode на CaseEvent ніде не редагується
    // (на відміну від occurredAt чи title), тож тут можна безпечно читати його "наживо"
    // через асоціацію - переносити в окрему заморожену колонку на Deadline не треба.
    public static String build(
            short durationValue,
            @NonNull DurationUnit durationUnit,
            @NonNull DayKind dayKind,
            @NonNull LocalDate baseDate,
            String legalBasis,
            @NonNull LocalDate naiveDueOn,
            @NonNull LocalDate dueOn,
            EventCode triggerEventCode
    ) {
        StringBuilder explanation = new StringBuilder()
                .append(durationValue)
                .append(' ')
                .append(durationPhrase(durationValue, durationUnit, dayKind))
                .append(" від ");

        if (triggerEventCode != null) {
            explanation.append(eventLabel(triggerEventCode)).append(' ');
        }

        explanation.append(DATE_FORMAT.format(baseDate));

        if (legalBasis != null && !legalBasis.isBlank()) {
            explanation.append(", ").append(legalBasis);
        }

        // Порівняння з naiveDueOn, а не перевірка "чи dueOn - робочий день": для робочих днів
        // (DayKind.WORKING) naiveDueOn і dueOn завжди рівні (там нема окремого кроку
        // "перенести", рахунок з самого початку йде тільки по робочих днях) - фраза про
        // перенесення з'явиться лише там, де воно справді сталось.
        if (!naiveDueOn.equals(dueOn)) {
            explanation
                    .append(", закінчення перенесено з ")
                    .append(WEEKDAY_GENITIVE.get(naiveDueOn.getDayOfWeek()))
                    .append(" на ")
                    .append(DATE_FORMAT.format(dueOn));
        }

        return explanation.toString();
    }

    // Навмисно БЕЗ "default ->": switch-вираз над enum без default сам є вичерпним - javac
    // відмовиться компілювати, якщо в EventCode з'явиться нове значення, а тут для нього не
    // додали гілку (замість того, щоб мовчки провалитись у default і показати порожній/
    // неправильний підпис). Якщо колись знадобиться "default" на перехідний період - це і
    // буде сигналом, що вичерпність свідомо вимкнули, а не забули про новий кейс.
    private static String eventLabel(EventCode eventCode) {
        return switch (eventCode) {
            case CLAIM_FILED -> "подання позовної заяви";
            case RULING_RECEIVED -> "отримання ухвали";
            case DECISION -> "ухвалення рішення";
            case COPY_SERVED -> "вручення копії";
            case HEARING -> "судового засідання";
            case OTHER -> "настання підстави";
        };
    }

    private static String durationPhrase(short value, DurationUnit unit, DayKind kind) {
        return switch (unit) {
            case MONTH -> monthWord(value);
            case DAY -> (kind == DayKind.WORKING ? "робочих " : "календарних ") + dayWord(value);
        };
    }

    private static String dayWord(short count) {
        return pluralize(count, "день", "дні", "днів");
    }

    private static String monthWord(short count) {
        return pluralize(count, "місяць", "місяці", "місяців");
    }

    // Українська плюралізація числівника: 11-14 (за модулем 100) -> форма "днів" навіть якщо
    // остання цифра 1-4 (одинадцять, а не один), інакше остання цифра 1 -> "день",
    // 2-4 -> "дні", решта (0, 5-9) -> "днів".
    private static String pluralize(short count, String one, String few, String many) {
        int mod100 = Math.abs(count) % 100;
        int mod10 = mod100 % 10;
        if (mod100 >= 11 && mod100 <= 14) {
            return many;
        }
        if (mod10 == 1) {
            return one;
        }
        if (mod10 >= 2 && mod10 <= 4) {
            return few;
        }
        return many;
    }
}

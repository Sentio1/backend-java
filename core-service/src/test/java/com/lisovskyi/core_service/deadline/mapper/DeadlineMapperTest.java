package com.lisovskyi.core_service.deadline.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.lisovskyi.core_service.case_.Case;
import com.lisovskyi.core_service.case_event.CaseEvent;
import com.lisovskyi.core_service.case_event.enums.EventCode;
import com.lisovskyi.core_service.deadline.Deadline;
import com.lisovskyi.core_service.deadline.dto.response.DeadlineResponse;
import com.lisovskyi.core_service.deadline_rule.DeadlineRule;
import com.lisovskyi.core_service.deadline_rule.enums.DayKind;
import com.lisovskyi.core_service.deadline_rule.enums.DurationUnit;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

// SEN-28: toResponse() мусить і заповнювати explanation через ExplanationBuilder (не тільки
// сам ExplanationBuilder ізольовано вміє правильний текст - MapStruct-згенерована
// DeadlineMapperImpl мала б реально його викликати), і не падати на старих дедлайнах, де
// знімку (durationValue/durationUnit/dayKind/baseDate/naiveDueOn) ще нема.
class DeadlineMapperTest {

    private final DeadlineMapper deadlineMapper = new DeadlineMapperImpl();

    private Deadline.DeadlineBuilder<?, ?> deadlineWithSnapshot() {
        Case case_ = Case.builder().id(10L).build();
        CaseEvent triggeringEvent =
                CaseEvent.builder().id(20L).eventCode(EventCode.COPY_SERVED).build();
        DeadlineRule rule = DeadlineRule.builder().id(30L).version((short) 2).build();

        return Deadline.builder()
                .id(1L)
                .case_(case_)
                .triggeringEvent(triggeringEvent)
                .rule(rule)
                .title("Подати відзив на позов")
                .legalBasis("ст. 354 ЦПК")
                .startsOn(LocalDate.of(2024, 6, 4))
                .dueOn(LocalDate.of(2024, 6, 10))
                .naiveDueOn(LocalDate.of(2024, 6, 9))
                .ruleVersion((short) 2)
                .baseDate(LocalDate.of(2024, 6, 3))
                .durationValue((short) 6)
                .durationUnit(DurationUnit.DAY)
                .dayKind(DayKind.CALENDAR);
    }

    @Test
    void toResponse_mapsIdsFromAssociations() {
        DeadlineResponse response = deadlineMapper.toResponse(deadlineWithSnapshot().build());

        assertThat(response.caseId()).isEqualTo(10L);
        assertThat(response.triggeringEventId()).isEqualTo(20L);
        assertThat(response.ruleId()).isEqualTo(30L);
    }

    @Test
    void toResponse_withFullSnapshot_buildsExplanationViaExplanationBuilder() {
        DeadlineResponse response = deadlineMapper.toResponse(deadlineWithSnapshot().build());

        assertThat(response.explanation())
                .isEqualTo("6 календарних днів від вручення копії 03.06.2024, ст. 354 ЦПК, "
                        + "закінчення перенесено з неділі на 10.06.2024");
    }

    @Test
    void toResponse_withoutTriggeringEvent_omitsEventLabelButStillExplains() {
        Deadline deadline = deadlineWithSnapshot().triggeringEvent(null).build();

        DeadlineResponse response = deadlineMapper.toResponse(deadline);

        assertThat(response.triggeringEventId()).isNull();
        assertThat(response.explanation())
                .isEqualTo("6 календарних днів від 03.06.2024, ст. 354 ЦПК, "
                        + "закінчення перенесено з неділі на 10.06.2024");
    }

    // ─── дедлайни, порахован до SEN-28 (знімку нема) ────────────────────────

    @Test
    void toResponse_missingSnapshotFields_returnsNullExplanation_insteadOfReadingLiveRule() {
        Deadline preSen28 = Deadline.builder()
                .id(2L)
                .case_(Case.builder().id(10L).build())
                .rule(DeadlineRule.builder().id(30L).build())
                .title("Подати відзив на позов")
                .legalBasis("ст. 354 ЦПК")
                .startsOn(LocalDate.of(2024, 6, 4))
                .dueOn(LocalDate.of(2024, 6, 10))
                // durationValue/durationUnit/dayKind/baseDate/naiveDueOn - усі відсутні,
                // як у рядків, порахованих до SEN-28.
                .build();

        DeadlineResponse response = deadlineMapper.toResponse(preSen28);

        assertThat(response.explanation()).isNull();
    }
}

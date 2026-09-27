package com.sentio.core_service.deadline.internal.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.sentio.core_service.litigation.api.enums.EventCode;
import com.sentio.core_service.deadline.internal.model.Deadline;
import com.sentio.core_service.deadline.internal.controller.dto.DeadlineResponse;
import com.sentio.core_service.deadline.internal.model.DeadlineRule;
import com.sentio.core_service.deadline.internal.enums.DayKind;
import com.sentio.core_service.deadline.internal.enums.DurationUnit;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

// SEN-28: toResponse() мусить і заповнювати explanation через ExplanationBuilder (не тільки
// сам ExplanationBuilder ізольовано вміє правильний текст - MapStruct-згенерована
// DeadlineMapperImpl мала б реально його викликати), і не падати на старих дедлайнах, де
// знімку (durationValue/durationUnit/dayKind/baseDate/naiveDueOn) ще нема.
class DeadlineMapperTest {

    private final DeadlineMapper deadlineMapper = new DeadlineMapperImpl();

    private Deadline.DeadlineBuilder<?, ?> deadlineWithSnapshot() {
        DeadlineRule rule = DeadlineRule.builder().id(30L).version((short) 2).build();

        return Deadline.builder()
                .id(1L)
                .caseId(10L)
                .triggeringEventId(20L)
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
        DeadlineResponse response = deadlineMapper.toResponse(deadlineWithSnapshot().build(), EventCode.COPY_SERVED);

        assertThat(response.caseId()).isEqualTo(10L);
        assertThat(response.triggeringEventId()).isEqualTo(20L);
        assertThat(response.ruleId()).isEqualTo(30L);
    }

    @Test
    void toResponse_withFullSnapshot_buildsExplanationViaExplanationBuilder() {
        DeadlineResponse response = deadlineMapper.toResponse(deadlineWithSnapshot().build(), EventCode.COPY_SERVED);

        assertThat(response.explanation())
                .isEqualTo("6 календарних днів від вручення копії 03.06.2024, ст. 354 ЦПК, "
                        + "закінчення перенесено з неділі на 10.06.2024");
    }

    @Test
    void toResponse_withoutTriggeringEvent_omitsEventLabelButStillExplains() {
        Deadline deadline = deadlineWithSnapshot().triggeringEventId(null).build();

        DeadlineResponse response = deadlineMapper.toResponse(deadline, null);

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
                .caseId(10L)
                .rule(DeadlineRule.builder().id(30L).build())
                .title("Подати відзив на позов")
                .legalBasis("ст. 354 ЦПК")
                .startsOn(LocalDate.of(2024, 6, 4))
                .dueOn(LocalDate.of(2024, 6, 10))
                // durationValue/durationUnit/dayKind/baseDate/naiveDueOn - усі відсутні,
                // як у рядків, порахованих до SEN-28.
                .build();

        DeadlineResponse response = deadlineMapper.toResponse(preSen28, null);

        assertThat(response.explanation()).isNull();
    }
}

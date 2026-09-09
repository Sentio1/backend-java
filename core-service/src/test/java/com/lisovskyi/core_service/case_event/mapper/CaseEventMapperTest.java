package com.lisovskyi.core_service.case_event.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.lisovskyi.core_service.case_.Case;
import com.lisovskyi.core_service.case_event.CaseEvent;
import com.lisovskyi.core_service.case_event.dto.response.CaseEventResponse;
import com.lisovskyi.core_service.case_event.enums.DeadlineResolution;
import com.lisovskyi.core_service.case_event.enums.EventCode;
import com.lisovskyi.core_service.deadline.Deadline;
import com.lisovskyi.core_service.deadline.enums.DeadlineStatus;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

// SEN-29 AC2: deadlineResolution - явний видимий стан на картці справи ("нема правила" vs
// "юрист відхилив" vs "усе гаразд"), обчислюється у CaseEventMapper.deadlineResolution() з
// того самого списку Deadline, що й deadlineIds - жодного тесту на цю логіку ще не було.
class CaseEventMapperTest {

    private final CaseEventMapper caseEventMapper = new CaseEventMapperImpl();

    private CaseEvent caseEvent() {
        return CaseEvent.builder()
                .id(1L)
                .case_(Case.builder().id(10L).build())
                .eventCode(EventCode.CLAIM_FILED)
                .build();
    }

    private Deadline deadline(long id, DeadlineStatus status) {
        return Deadline.builder()
                .id(id)
                .status(status)
                .startsOn(LocalDate.of(2024, 6, 1))
                .dueOn(LocalDate.of(2024, 6, 10))
                .build();
    }

    @Test
    void toResponse_noDeadlines_resolvesToNoRuleMatched() {
        CaseEventResponse response = caseEventMapper.toResponse(caseEvent(), List.of());

        assertThat(response.deadlineIds()).isEmpty();
        assertThat(response.deadlineResolution()).isEqualTo(DeadlineResolution.NO_RULE_MATCHED);
    }

    @Test
    void toResponse_pendingDeadline_resolvesToRuleApplied() {
        CaseEventResponse response = caseEventMapper.toResponse(caseEvent(), List.of(deadline(100L, DeadlineStatus.PENDING)));

        assertThat(response.deadlineIds()).containsExactly(100L);
        assertThat(response.deadlineResolution()).isEqualTo(DeadlineResolution.RULE_APPLIED);
    }

    @Test
    void toResponse_allDeadlinesRejected_resolvesToRejected() {
        CaseEventResponse response = caseEventMapper.toResponse(
                caseEvent(), List.of(deadline(100L, DeadlineStatus.REJECTED), deadline(101L, DeadlineStatus.REJECTED)));

        assertThat(response.deadlineResolution()).isEqualTo(DeadlineResolution.REJECTED);
    }

    // Один з кількох (SEN-29 AC1: подія може мати кілька дедлайнів) все ще активний - подія
    // не має вважатись "відхиленою", доки лишається хоч один невідхилений дедлайн.
    @Test
    void toResponse_oneRejectedOnePending_resolvesToRuleApplied_notRejected() {
        CaseEventResponse response = caseEventMapper.toResponse(
                caseEvent(), List.of(deadline(100L, DeadlineStatus.REJECTED), deadline(101L, DeadlineStatus.PENDING)));

        assertThat(response.deadlineIds()).containsExactlyInAnyOrder(100L, 101L);
        assertThat(response.deadlineResolution()).isEqualTo(DeadlineResolution.RULE_APPLIED);
    }

    @Test
    void toResponse_mapsIdAndCaseIdFromAssociations() {
        CaseEventResponse response = caseEventMapper.toResponse(caseEvent(), List.of());

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.caseId()).isEqualTo(10L);
    }
}

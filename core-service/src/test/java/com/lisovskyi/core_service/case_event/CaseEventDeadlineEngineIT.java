package com.lisovskyi.core_service.case_event;

import com.lisovskyi.core_service.TestcontainersConfiguration;
import com.lisovskyi.core_service.case_.Case;
import com.lisovskyi.core_service.case_.CaseRepository;
import com.lisovskyi.core_service.case_.enums.ProcedureType;
import com.lisovskyi.core_service.case_event.dto.request.CaseEventManualRegisterRequest;
import com.lisovskyi.core_service.case_event.dto.response.CaseEventResponse;
import com.lisovskyi.core_service.case_event.enums.EventCode;
import com.lisovskyi.core_service.court.Court;
import com.lisovskyi.core_service.deadline.Deadline;
import com.lisovskyi.core_service.deadline.DeadlineRepository;
import com.lisovskyi.core_service.deadline_rule.DeadlineRule;
import com.lisovskyi.core_service.deadline_rule.DeadlineRuleRepository;
import com.lisovskyi.core_service.deadline_rule.enums.CountFrom;
import com.lisovskyi.core_service.deadline_rule.enums.DayKind;
import com.lisovskyi.core_service.deadline_rule.enums.DurationUnit;
import com.sentio.shared.entity.id.case_.CaseId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import com.sentio.shared.entity.id.user.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// Наскрізний тест deadlineEngine (SEN-19): від CaseEventService.registerCaseEvent через
// DeadlineRuleRepository.findByActiveRule і WorkingDayCalculator/CALENDAR-арифметику до
// реального рядка core.deadlines у БД — саме той рівень, що ловить помилки склейки шматків,
// а не кожен шматок окремо (юніт-тести на це вже є в WorkingDayCalculatorTest).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class CaseEventDeadlineEngineIT {

    @Autowired
    private CaseEventService caseEventService;

    @Autowired
    private CaseEventRepository caseEventRepository;

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private DeadlineRuleRepository deadlineRuleRepository;

    @Autowired
    private DeadlineRepository deadlineRepository;

    @Autowired
    private EntityManager entityManager;

    private void flushAndDetach() {
        entityManager.flush();
        entityManager.clear();
    }

    private Court persistCourt() {
        Court court = Court.builder()
                .name("Печерський районний суд м. Києва")
                .code("757")
                .instance((short) 1)
                .timeZone("Europe/Kyiv")
                .isActive(true)
                .build();
        entityManager.persist(court);
        return court;
    }

    private Case persistCase(Long organizationId, Court court) {
        Case case_ = Case.builder()
                .organizationId(organizationId)
                .responsibleUserId(1L)
                .title("Позов про стягнення заборгованості")
                .procedure(ProcedureType.CIVIL)
                .court(court)
                .build();
        return caseRepository.save(case_);
    }

    private DeadlineRule persistCalendarRule() {
        DeadlineRule rule = DeadlineRule.builder()
                .code("TEST_CLAIM_FILED_CALENDAR")
                .procedure(ProcedureType.CIVIL)
                .triggerEventCode(EventCode.CLAIM_FILED)
                .title("Подати відзив на позов")
                .legalBasis("ст. 178 ЦПК України")
                .durationValue((short) 5)
                .durationUnit(DurationUnit.DAY)
                .dayKind(DayKind.CALENDAR)
                .countFrom(CountFrom.NEXT_DAY)
                .isExtendable(false)
                .validFrom(LocalDate.of(2020, 1, 1))
                .build();
        return deadlineRuleRepository.save(rule);
    }

    @Test
    void registerCaseEvent_createsDeadlineWithExpectedDueOn_andRecalculatesInPlace_withoutDuplicating() {
        Long organizationId = 5_001L;
        Court court = persistCourt();
        Case case_ = persistCase(organizationId, court);
        DeadlineRule rule = persistCalendarRule();
        flushAndDetach();

        // 2024-06-03 09:00 UTC = 2024-06-03 12:00 Europe/Kyiv (літній час) - понеділок,
        // без прикордонних ефектів зміни дати між UTC і зоною суду.
        Instant occurredAt = Instant.parse("2024-06-03T09:00:00Z");
        CaseEventManualRegisterRequest request = CaseEventManualRegisterRequest.builder()
                .eventCode(EventCode.CLAIM_FILED)
                .title("Отримано позовну заяву")
                .occurredAt(occurredAt)
                .build();

        CaseEventResponse response = caseEventService.registerCaseEvent(
                CaseId.of(case_.getId()), OrganizationId.of(organizationId), UserId.of(1L), request);
        flushAndDetach();

        // startsOn = occurredAt (03.06, пн) + 1 день (NEXT_DAY) = 04.06
        // dueOn = startsOn + 5 календарних днів (CALENDAR) = 09.06
        assertThat(response.deadlineId()).isNotNull();
        Deadline firstDeadline = deadlineRepository.findById(response.deadlineId()).orElseThrow();
        assertThat(firstDeadline.getStartsOn()).isEqualTo(LocalDate.of(2024, 6, 4));
        assertThat(firstDeadline.getDueOn()).isEqualTo(LocalDate.of(2024, 6, 9));
        assertThat(firstDeadline.getTitle()).isEqualTo(rule.getTitle());
        assertThat(deadlineRepository.count()).isEqualTo(1);

        // Симулюємо майбутній SEN-19 AC: правку occurredAt на вже існуючій події і повторний
        // виклик рушія для того самого CaseEvent — має оновити той самий Deadline, а не
        // створити другий.
        CaseEvent caseEvent = caseEventRepository.findById(response.id()).orElseThrow();
        caseEvent.setOccurredAt(occurredAt.plus(Duration.ofDays(2)));
        caseEventRepository.save(caseEvent);
        flushAndDetach();

        CaseEvent reloadedCaseEvent = caseEventRepository.findById(response.id()).orElseThrow();
        Long secondDeadlineId = caseEventService.deadlineEngine(reloadedCaseEvent);
        flushAndDetach();

        // Нова startsOn = 06.06 (05.06 + 1), нова dueOn = 11.06 (06.06 + 5) - і той самий рядок.
        assertThat(secondDeadlineId).isEqualTo(response.deadlineId());
        assertThat(deadlineRepository.count()).isEqualTo(1);
        Deadline recalculated = deadlineRepository.findById(secondDeadlineId).orElseThrow();
        assertThat(recalculated.getStartsOn()).isEqualTo(LocalDate.of(2024, 6, 6));
        assertThat(recalculated.getDueOn()).isEqualTo(LocalDate.of(2024, 6, 11));
    }
}

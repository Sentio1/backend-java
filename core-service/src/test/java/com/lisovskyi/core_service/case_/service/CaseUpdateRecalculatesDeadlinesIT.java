package com.lisovskyi.core_service.case_.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lisovskyi.core_service.TestcontainersConfiguration;
import com.lisovskyi.core_service.case_.Case;
import com.lisovskyi.core_service.case_.CaseRepository;
import com.lisovskyi.core_service.case_.CaseService;
import com.lisovskyi.core_service.case_.dto.request.CaseUpdateRequest;
import com.lisovskyi.core_service.case_.enums.CaseInstance;
import com.lisovskyi.core_service.case_.enums.ProcedureType;
import com.lisovskyi.core_service.case_event.CaseEventService;
import com.lisovskyi.core_service.case_event.dto.request.CaseEventManualRegisterRequest;
import com.lisovskyi.core_service.case_event.dto.response.CaseEventResponse;
import com.lisovskyi.core_service.case_event.enums.EventCode;
import com.lisovskyi.core_service.court.Court;
import com.lisovskyi.core_service.court.CourtInstance;
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
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

// SEN-21 AC-нотатка: "Зміна [виду судочинства/інстанції] після створення справи тягне
// перерахунок усіх строків". Регресія: DeadlineEngine.generateDeadline раніше шукав наявний
// Deadline по парі (triggeringEvent, rule) - коли зміна procedure підбирає ІНШЕ правило (інший
// рядок DeadlineRule), цей пошук ніколи не знаходив уже наявний дедлайн, і замість оновлення на
// місці з'являвся другий, дублюючий рядок Deadline для тієї самої події.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class CaseUpdateRecalculatesDeadlinesIT {

    @Autowired
    private CaseService caseService;

    @Autowired
    private CaseEventService caseEventService;

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
                .code("757-recalc-it")
                .courtInstance(CourtInstance.FIRST)
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
                .instance(CaseInstance.FIRST)
                .court(court)
                .build();
        return caseRepository.save(case_);
    }

    private DeadlineRule persistRule(ProcedureType procedure, short durationValue) {
        DeadlineRule rule = DeadlineRule.builder()
                .code("TEST_" + procedure + "_CLAIM_FILED")
                .procedure(procedure)
                .courtInstance(CourtInstance.FIRST)
                .triggerEventCode(EventCode.CLAIM_FILED)
                .title("Подати відзив на позов")
                .legalBasis("ст. 178")
                .durationValue(durationValue)
                .durationUnit(DurationUnit.DAY)
                .dayKind(DayKind.CALENDAR)
                .countFrom(CountFrom.NEXT_DAY)
                .isExtendable(false)
                .validFrom(LocalDate.of(2020, 1, 1))
                .build();
        return deadlineRuleRepository.save(rule);
    }

    @Test
    void updateCase_withProcedureChange_recalculatesInPlace_withoutDuplicatingDeadline() {
        Long organizationId = 8_001L;
        Court court = persistCourt();
        Case case_ = persistCase(organizationId, court);
        DeadlineRule civilRule = persistRule(ProcedureType.CIVIL, (short) 5);
        DeadlineRule commercialRule = persistRule(ProcedureType.COMMERCIAL, (short) 10);
        flushAndDetach();

        // 2024-06-03 09:00 UTC = 2024-06-03 12:00 Europe/Kyiv (літній час), понеділок.
        CaseEventManualRegisterRequest eventRequest = CaseEventManualRegisterRequest.builder()
                .eventCode(EventCode.CLAIM_FILED)
                .title("Отримано позовну заяву")
                .occurredAt(Instant.parse("2024-06-03T09:00:00Z"))
                .build();
        CaseEventResponse eventResponse = caseEventService.registerCaseEvent(
                CaseId.of(case_.getId()), OrganizationId.of(organizationId), UserId.of(1L), eventRequest);
        flushAndDetach();

        // startsOn = 04.06 (occurredAt + 1 NEXT_DAY), dueOn (CIVIL, 5 календарних) = 09.06.
        assertThat(eventResponse.deadlineId()).isNotNull();
        Long deadlineId = eventResponse.deadlineId();
        Deadline firstDeadline = deadlineRepository.findById(deadlineId).orElseThrow();
        assertThat(firstDeadline.getRule().getId()).isEqualTo(civilRule.getId());
        assertThat(firstDeadline.getDueOn()).isEqualTo(LocalDate.of(2024, 6, 9));
        assertThat(deadlineRepository.count()).isEqualTo(1);

        CaseUpdateRequest procedureChangeRequest = new CaseUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.of(ProcedureType.COMMERCIAL),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined());

        caseService.updateCase(CaseId.of(case_.getId()), OrganizationId.of(organizationId), UserId.of(1L), procedureChangeRequest);
        flushAndDetach();

        // Той самий рядок Deadline, тепер під COMMERCIAL-правилом (dueOn = 04.06 + 10 = 14.06) -
        // не другий, дублюючий рядок.
        assertThat(deadlineRepository.count()).isEqualTo(1);
        Deadline recalculated = deadlineRepository.findById(deadlineId).orElseThrow();
        assertThat(recalculated.getRule().getId()).isEqualTo(commercialRule.getId());
        assertThat(recalculated.getDueOn()).isEqualTo(LocalDate.of(2024, 6, 14));
    }
}

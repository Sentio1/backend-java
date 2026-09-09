package com.lisovskyi.core_service.case_event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lisovskyi.core_service.TestcontainersConfiguration;
import com.lisovskyi.core_service.case_.Case;
import com.lisovskyi.core_service.case_.CaseRepository;
import com.lisovskyi.core_service.case_.enums.CaseInstance;
import com.lisovskyi.core_service.case_.enums.ProcedureType;
import com.lisovskyi.core_service.case_event.dto.request.CaseEventManualRegisterRequest;
import com.lisovskyi.core_service.case_event.dto.response.CaseEventResponse;
import com.lisovskyi.core_service.case_event.enums.EventCode;
import com.lisovskyi.core_service.court.Court;
import com.lisovskyi.core_service.court.CourtInstance;
import com.lisovskyi.core_service.deadline.Deadline;
import com.lisovskyi.core_service.deadline.DeadlineRepository;
import com.lisovskyi.core_service.deadline.DeadlineService;
import com.lisovskyi.core_service.deadline.dto.request.DeadlineManualRegisterRequest;
import com.lisovskyi.core_service.deadline.dto.response.DeadlineResponse;
import com.lisovskyi.core_service.deadline.enums.DeadlineSource;
import com.lisovskyi.core_service.deadline_processor.DeadlineEngine;
import com.lisovskyi.core_service.deadline_rule.DeadlineRule;
import com.lisovskyi.core_service.deadline_rule.DeadlineRuleRepository;
import com.lisovskyi.core_service.deadline_rule.enums.CountFrom;
import com.lisovskyi.core_service.deadline_rule.enums.DayKind;
import com.lisovskyi.core_service.deadline_rule.enums.DurationUnit;
import com.sentio.shared.entity.id.case_.CaseId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import com.sentio.shared.entity.id.user.UserId;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

// Наскрізний тест deadlineEngine (SEN-19): від CaseEventService.registerCaseEvent через
// DeadlineRuleRepository.findByActiveRule і DeadlineCalculator-арифметику до реального рядка
// core.deadlines у БД — саме той рівень, що ловить помилки склейки шматків, а не кожен шматок
// окремо (юніт-тести на арифметику саму по собі вже є в DeadlineCalculatorTest).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class CaseEventDeadlineEngineIT {

    @Autowired
    private CaseEventService caseEventService;

    @Autowired
    private DeadlineEngine deadlineEngine;

    @Autowired
    private CaseEventRepository caseEventRepository;

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private DeadlineRuleRepository deadlineRuleRepository;

    @Autowired
    private DeadlineRepository deadlineRepository;

    @Autowired
    private DeadlineService deadlineService;

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

    private DeadlineRule persistCalendarRule() {
        DeadlineRule rule = DeadlineRule.builder()
                .code("TEST_CLAIM_FILED_CALENDAR")
                .procedure(ProcedureType.CIVIL)
                .courtInstance(CourtInstance.FIRST)
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

    // Другий, паралельний рядок правила на ту саму пару (procedure, instance, triggerEventCode) -
    // інший code, тож exclude_overlapping_rule_versions (V29, лише в межах одного code) йому не
    // заважає. Різні тривалості (5 vs 10) - щоб легко відрізнити, який дедлайн від якого правила.
    private DeadlineRule persistSecondCalendarRule() {
        DeadlineRule rule = DeadlineRule.builder()
                .code("TEST_CLAIM_FILED_SECOND")
                .procedure(ProcedureType.CIVIL)
                .courtInstance(CourtInstance.FIRST)
                .triggerEventCode(EventCode.CLAIM_FILED)
                .title("Подати клопотання про роз'яснення")
                .legalBasis("ст. 271 ЦПК України")
                .durationValue((short) 10)
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
        // dueOn = startsOn + 5 календарних днів (CALENDAR) = 09.06 (неділя) → переноситься
        // на найближчий робочий день (SEN-27), тобто на понеділок 10.06.
        assertThat(response.deadlineIds()).hasSize(1);
        Long firstDeadlineId = response.deadlineIds().getFirst();
        Deadline firstDeadline = deadlineRepository.findById(firstDeadlineId).orElseThrow();
        assertThat(firstDeadline.getStartsOn()).isEqualTo(LocalDate.of(2024, 6, 4));
        assertThat(firstDeadline.getDueOn()).isEqualTo(LocalDate.of(2024, 6, 10));
        assertThat(firstDeadline.getTitle()).isEqualTo(rule.getTitle());
        assertThat(deadlineRepository.count()).isEqualTo(1);

        // SEN-28: знімок правила й дати в момент розрахунку - і на первинному створенні
        // теж, не лише при перерахунку.
        assertThat(firstDeadline.getRuleVersion()).isEqualTo(rule.getVersion());
        assertThat(firstDeadline.getBaseDate()).isEqualTo(LocalDate.of(2024, 6, 3));
        // naiveDueOn (09.06, неділя) - дата до перенесення календарем на 10.06.
        assertThat(firstDeadline.getNaiveDueOn()).isEqualTo(LocalDate.of(2024, 6, 9));

        // Симулюємо майбутній SEN-19 AC: правку occurredAt на вже існуючій події і повторний
        // виклик рушія для того самого CaseEvent — має оновити той самий Deadline, а не
        // створити другий.
        CaseEvent caseEvent = caseEventRepository.findById(response.id()).orElseThrow();
        caseEvent.setOccurredAt(occurredAt.plus(Duration.ofDays(2)));
        caseEventRepository.save(caseEvent);
        flushAndDetach();

        CaseEvent reloadedCaseEvent =
                caseEventRepository.findById(response.id()).orElseThrow();
        List<Deadline> secondDeadlines = deadlineEngine.generateDeadline(reloadedCaseEvent, 1L);
        flushAndDetach();

        // Нова startsOn = 06.06 (05.06 + 1), нова dueOn = 11.06 (06.06 + 5) - і той самий рядок.
        assertThat(secondDeadlines).extracting(Deadline::getId).containsExactly(firstDeadlineId);
        assertThat(deadlineRepository.count()).isEqualTo(1);
        Deadline recalculated = deadlineRepository.findById(firstDeadlineId).orElseThrow();
        assertThat(recalculated.getStartsOn()).isEqualTo(LocalDate.of(2024, 6, 6));
        assertThat(recalculated.getDueOn()).isEqualTo(LocalDate.of(2024, 6, 11));

        // Знімок оновлюється разом з перерахунком: нова baseDate (05.06), і 11.06 (вівторок) -
        // робочий день, перенесення календарем нема, тож naiveDueOn == dueOn.
        assertThat(recalculated.getRuleVersion()).isEqualTo(rule.getVersion());
        assertThat(recalculated.getBaseDate()).isEqualTo(LocalDate.of(2024, 6, 5));
        assertThat(recalculated.getNaiveDueOn()).isEqualTo(LocalDate.of(2024, 6, 11));
    }

    // SEN-29 AC1: одна подія одночасно підпадає під ДВА активних правила (різний code, той
    // самий procedure+instance+triggerEventCode) - реальний JPQL findAllActiveRules і реальний
    // uq_deadlines_triggering_event_rule мусять дозволити обидва рядки одночасно, а не
    // конфліктувати чи мовчки взяти лише один (те, що з Optional<DeadlineRule> раніше
    // фізично не могло статись - зараз саме той шлях, який DeadlineEngineTest перевіряє на
    // моках, а тут - наскрізно проти реальної БД).
    @Test
    void registerCaseEvent_withTwoActiveRules_createsTwoIndependentDeadlines() {
        Long organizationId = 5_002L;
        Court court = persistCourt();
        Case case_ = persistCase(organizationId, court);
        DeadlineRule firstRule = persistCalendarRule();
        DeadlineRule secondRule = persistSecondCalendarRule();
        flushAndDetach();

        Instant occurredAt = Instant.parse("2024-06-03T09:00:00Z");
        CaseEventManualRegisterRequest request = CaseEventManualRegisterRequest.builder()
                .eventCode(EventCode.CLAIM_FILED)
                .title("Отримано позовну заяву")
                .occurredAt(occurredAt)
                .build();

        CaseEventResponse response = caseEventService.registerCaseEvent(
                CaseId.of(case_.getId()), OrganizationId.of(organizationId), UserId.of(1L), request);
        flushAndDetach();

        assertThat(response.deadlineIds()).hasSize(2);
        assertThat(deadlineRepository.count()).isEqualTo(2);
        List<Deadline> deadlines = deadlineRepository.findAllById(response.deadlineIds());
        assertThat(deadlines).extracting(d -> d.getRule().getId())
                .containsExactlyInAnyOrder(firstRule.getId(), secondRule.getId());
        // startsOn 04.06 (вт) + 5 календарних (перше правило) = 09.06 (нд) -> переноситься на
        // 10.06 (пн); startsOn + 10 календарних (друге правило) = 14.06 (пт) - уже робочий
        // день, перенесення не треба.
        assertThat(deadlines).extracting(Deadline::getDueOn)
                .containsExactlyInAnyOrder(LocalDate.of(2024, 6, 10), LocalDate.of(2024, 6, 14));
    }

    // SEN-29 AC3 x AC1: ручний дедлайн, прив'язаний до події, не повинен постраждати, коли ту
    // саму подію перераховують (bugfix у DeadlineEngine.reconcile() - без фільтра source==RULE
    // позиційний fallback міг би підхопити цей рядок як "вільний слот" і переписати його під
    // розраховане правило).
    @Test
    void createManualDeadline_attachedToEvent_isNotTouchedByLaterRecalculation() {
        Long organizationId = 5_003L;
        Court court = persistCourt();
        Case case_ = persistCase(organizationId, court);
        DeadlineRule rule = persistCalendarRule();
        flushAndDetach();

        Instant occurredAt = Instant.parse("2024-06-03T09:00:00Z");
        CaseEventManualRegisterRequest request = CaseEventManualRegisterRequest.builder()
                .eventCode(EventCode.CLAIM_FILED)
                .title("Отримано позовну заяву")
                .occurredAt(occurredAt)
                .build();
        CaseEventResponse eventResponse = caseEventService.registerCaseEvent(
                CaseId.of(case_.getId()), OrganizationId.of(organizationId), UserId.of(1L), request);
        flushAndDetach();

        DeadlineManualRegisterRequest manualRequest = new DeadlineManualRegisterRequest(
                "Нагадати клієнту про сплату судового збору",
                LocalDate.of(2024, 6, 1),
                LocalDate.of(2024, 6, 20),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.of(eventResponse.id()));
        DeadlineResponse manualDeadline = deadlineService.createManualDeadline(
                CaseId.of(case_.getId()), OrganizationId.of(organizationId), UserId.of(1L), manualRequest);
        flushAndDetach();
        assertThat(manualDeadline.source()).isEqualTo(DeadlineSource.MANUAL);
        assertThat(manualDeadline.createdBy()).isEqualTo(1L);
        assertThat(deadlineRepository.count()).isEqualTo(2);

        // Перерахунок тієї самої події (зміна occurredAt) - лише правило-дедлайн має
        // перерахуватись, ручний лишитись повністю незмінним.
        CaseEvent caseEvent =
                caseEventRepository.findById(eventResponse.id()).orElseThrow();
        caseEvent.setOccurredAt(occurredAt.plus(Duration.ofDays(2)));
        caseEventRepository.save(caseEvent);
        flushAndDetach();

        CaseEvent reloadedCaseEvent =
                caseEventRepository.findById(eventResponse.id()).orElseThrow();
        List<Deadline> recalculated = deadlineEngine.generateDeadline(reloadedCaseEvent, 1L);
        flushAndDetach();

        assertThat(recalculated).hasSize(1);
        assertThat(recalculated.getFirst().getRule().getId()).isEqualTo(rule.getId());
        assertThat(deadlineRepository.count()).isEqualTo(2);

        Deadline manualAfterRecalc = deadlineRepository.findById(manualDeadline.id()).orElseThrow();
        assertThat(manualAfterRecalc.getSource()).isEqualTo(DeadlineSource.MANUAL);
        assertThat(manualAfterRecalc.getRule()).isNull();
        assertThat(manualAfterRecalc.getTitle()).isEqualTo("Нагадати клієнту про сплату судового збору");
        assertThat(manualAfterRecalc.getDueOn()).isEqualTo(LocalDate.of(2024, 6, 20));
    }

    // SEN-29 AC3: перевіряє, що deadlines_source_rule_id_consistency_check (V40) реально
    // застосований у БД, а не лише декларований у міграції - RULE без rule_id має впасти на
    // flush, не пройти мовчки.
    @Test
    void deadlines_sourceRuleConsistencyCheck_rejectsRuleSourcedDeadlineWithoutARule() {
        Long organizationId = 5_004L;
        Court court = persistCourt();
        Case case_ = persistCase(organizationId, court);
        flushAndDetach();

        Deadline invalid = Deadline.builder()
                .organizationId(organizationId)
                .case_(case_)
                .rule(null)
                .source(DeadlineSource.RULE)
                .title("Некоректний рядок")
                .startsOn(LocalDate.of(2024, 1, 1))
                .dueOn(LocalDate.of(2024, 1, 10))
                .build();

        assertThatThrownBy(() -> deadlineRepository.saveAndFlush(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

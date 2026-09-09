package com.lisovskyi.core_service.deadline_processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lisovskyi.core_service.audit_log.AuditLogService;
import com.lisovskyi.core_service.case_.Case;
import com.lisovskyi.core_service.case_.enums.ProcedureType;
import com.lisovskyi.core_service.case_event.CaseEvent;
import com.lisovskyi.core_service.case_event.enums.EventCode;
import com.lisovskyi.core_service.court.Court;
import com.lisovskyi.core_service.court.CourtInstance;
import com.lisovskyi.core_service.deadline.Deadline;
import com.lisovskyi.core_service.deadline.DeadlineRepository;
import com.lisovskyi.core_service.deadline.enums.DeadlineSource;
import com.lisovskyi.core_service.deadline.enums.DeadlineStatus;
import com.lisovskyi.core_service.deadline.finder.DeadlineFinder;
import com.lisovskyi.core_service.deadline_rule.DeadlineRule;
import com.lisovskyi.core_service.deadline_rule.enums.CountFrom;
import com.lisovskyi.core_service.deadline_rule.enums.DayKind;
import com.lisovskyi.core_service.deadline_rule.enums.DurationUnit;
import com.lisovskyi.core_service.deadline_rule.finder.DeadlineRuleFinder;
import com.lisovskyi.core_service.holiday.finder.HolidayFinder;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

// SEN-29 AC1/AC3/AC5: DeadlineEngine.generateDeadline пройшло з "0 чи 1 Deadline на подію" на
// "0..N" (по одному на кожне активне DeadlineRule), і саме reconcile()/saveWithRaceRecovery -
// нова, ще не покрита ані одним існуючим тестом логіка (наскрізні *IT досі перевіряють лише
// сценарій з рівно одним активним правилом). Pure Mockito - DB-сторону (реальний JPQL-запит
// findAllActiveRules, унікальний індекс uq_deadlines_triggering_event_rule) покривають
// CaseEventDeadlineEngineIT/CaseUpdateRecalculatesDeadlinesIT.
@ExtendWith(MockitoExtension.class)
class DeadlineEngineTest {

    @Mock
    private DeadlineRepository deadlineRepository;

    @Mock
    private DeadlineFinder deadlineFinder;

    @Mock
    private DeadlineRuleFinder deadlineRuleFinder;

    @Mock
    private HolidayFinder holidayFinder;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private DeadlineEngine deadlineEngine;

    // occurredAt (03.06.2024, пн) + NEXT_DAY = startsOn 04.06; +5 CALENDAR днів = 09.06 (нд),
    // перенесення на робочий день (SEN-27) = dueOn 10.06 - той самий фікстур, що вже перевірений
    // у CaseEventDeadlineEngineIT, аби не перевинаходити арифметику дат у новому тесті.
    private static final Instant OCCURRED_AT = Instant.parse("2024-06-03T09:00:00Z");
    private static final LocalDate EXPECTED_STARTS_ON = LocalDate.of(2024, 6, 4);
    private static final LocalDate EXPECTED_DUE_ON = LocalDate.of(2024, 6, 10);

    private final AtomicLong idSequence = new AtomicLong(1000);

    private Court court() {
        return Court.builder().id(1L).courtInstance(CourtInstance.FIRST).timeZone("Europe/Kyiv").build();
    }

    private Case case_(Court court) {
        return Case.builder()
                .id(100L)
                .organizationId(5001L)
                .procedure(ProcedureType.CIVIL)
                .court(court)
                .build();
    }

    private CaseEvent event(Case case_) {
        return CaseEvent.builder()
                .id(200L)
                .organizationId(5001L)
                .case_(case_)
                .eventCode(EventCode.CLAIM_FILED)
                .occurredAt(OCCURRED_AT)
                .build();
    }

    private DeadlineRule rule(long id, String code) {
        return DeadlineRule.builder()
                .id(id)
                .code(code)
                .procedure(ProcedureType.CIVIL)
                .courtInstance(CourtInstance.FIRST)
                .triggerEventCode(EventCode.CLAIM_FILED)
                .title("Подати відзив на позов")
                .legalBasis("ст. 178 ЦПК України")
                .durationValue((short) 5)
                .durationUnit(DurationUnit.DAY)
                .dayKind(DayKind.CALENDAR)
                .countFrom(CountFrom.NEXT_DAY)
                .version((short) 1)
                .build();
    }

    // Deadline.rule - @ManyToOne LAZY: у реальному Hibernate-проксі getCode() працює й без
    // ініціалізованого проксі лише тому, що ми тут будуємо звичайні POJO, не проксі.
    private Deadline existingDeadline(CaseEvent event, DeadlineRule rule, LocalDate dueOn) {
        return Deadline.builder()
                .id(idSequence.incrementAndGet())
                .organizationId(event.getOrganizationId())
                .case_(event.getCase_())
                .triggeringEvent(event)
                .rule(rule)
                .source(DeadlineSource.RULE)
                .title(rule.getTitle())
                .startsOn(EXPECTED_STARTS_ON)
                .dueOn(dueOn)
                .ruleVersion(rule.getVersion())
                .build();
    }

    private void stubNoHolidays() {
        when(holidayFinder.findWorkingDayOverrides(any(), any(), any())).thenReturn(Map.of());
    }

    // save() повертає той самий об'єкт з новим id - імітує поведінку JpaRepository.save на INSERT.
    private void stubSaveAssignsId() {
        when(deadlineRepository.save(any())).thenAnswer(invocation -> {
            Deadline deadline = invocation.getArgument(0);
            if (deadline.getId() == null) {
                deadline.setId(idSequence.incrementAndGet());
            }
            return deadline;
        });
    }

    // ─── court/rules absent - швидкий вихід, нічого не рахує ────────────────

    @Test
    void generateDeadline_caseWithoutCourt_returnsEmptyList_andNeverQueriesRules() {
        Case caseWithoutCourt = Case.builder().id(101L).organizationId(5001L).procedure(ProcedureType.CIVIL).build();
        CaseEvent event = event(caseWithoutCourt);

        List<Deadline> result = deadlineEngine.generateDeadline(event, 1L);

        assertThat(result).isEmpty();
        verify(deadlineRuleFinder, never()).findAllActiveRules(any(), any(), any(), any());
    }

    @Test
    void generateDeadline_noActiveRules_returnsEmptyList_andNeverSaves() {
        Court court = court();
        CaseEvent event = event(case_(court));
        when(deadlineRuleFinder.findAllActiveRules(any(), any(), any(), any())).thenReturn(List.of());

        List<Deadline> result = deadlineEngine.generateDeadline(event, 1L);

        assertThat(result).isEmpty();
        verify(deadlineRepository, never()).save(any());
    }

    // ─── AC1: кілька активних правил на одну подію ──────────────────────────

    @Test
    void generateDeadline_singleActiveRule_noExisting_createsOneDeadline() {
        Court court = court();
        CaseEvent event = event(case_(court));
        DeadlineRule rule = rule(1L, "CODE_A");
        when(deadlineRuleFinder.findAllActiveRules(any(), any(), any(), any())).thenReturn(List.of(rule));
        when(deadlineFinder.findAllByTriggeringEvent(event)).thenReturn(List.of());
        stubNoHolidays();
        stubSaveAssignsId();

        List<Deadline> result = deadlineEngine.generateDeadline(event, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getRule()).isEqualTo(rule);
        assertThat(result.getFirst().getDueOn()).isEqualTo(EXPECTED_DUE_ON);
        assertThat(result.getFirst().getSource()).isEqualTo(DeadlineSource.RULE);
        // Первинне створення - нема "старої" дати для порівняння, аудит не пишеться
        // (той самий принцип, що й CaseService.updateCase не аудитує createCase).
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any(), any());
        verify(auditLogService, never()).logSystemChange(any(), any(), any(), any(), any(), any());
    }

    @Test
    void generateDeadline_twoActiveRules_createsOneDeadlinePerRule() {
        Court court = court();
        CaseEvent event = event(case_(court));
        DeadlineRule ruleA = rule(1L, "CODE_A");
        DeadlineRule ruleB = rule(2L, "CODE_B");
        when(deadlineRuleFinder.findAllActiveRules(any(), any(), any(), any())).thenReturn(List.of(ruleA, ruleB));
        when(deadlineFinder.findAllByTriggeringEvent(event)).thenReturn(List.of());
        stubNoHolidays();
        stubSaveAssignsId();

        List<Deadline> result = deadlineEngine.generateDeadline(event, 1L);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Deadline::getRule).containsExactlyInAnyOrder(ruleA, ruleB);
        verify(deadlineRepository, times(2)).save(any());
    }

    @Test
    void generateDeadline_existingDeadlineWithMatchingRuleCode_updatesInPlace_notDuplicate() {
        Court court = court();
        CaseEvent event = event(case_(court));
        DeadlineRule rule = rule(1L, "CODE_A");
        Deadline existing = existingDeadline(event, rule, LocalDate.of(2024, 6, 1));
        when(deadlineRuleFinder.findAllActiveRules(any(), any(), any(), any())).thenReturn(List.of(rule));
        when(deadlineFinder.findAllByTriggeringEvent(event)).thenReturn(List.of(existing));
        stubNoHolidays();
        when(deadlineRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<Deadline> result = deadlineEngine.generateDeadline(event, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(existing.getId());
        assertThat(result.getFirst().getDueOn()).isEqualTo(EXPECTED_DUE_ON);
        verify(deadlineRepository, times(1)).save(any());
        // dueOn реально змінився (01.06 -> 10.06) - разом з людиною-автором (changedBy=1L).
        verify(auditLogService)
                .log(any(), any(), any(), any(), org.mockito.ArgumentMatchers.eq("dueOn"), any(), any());
    }

    // Регресія SEN-21 (CaseUpdateRecalculatesDeadlinesIT доводить те саме наскрізно): зміна
    // procedure/instance справи підбирає ІНШЕ правило (інший code) на місце старого - без
    // позиційного fallback-проходу reconcile() другий рядок Deadline дублював би перший
    // замість оновлення на місці.
    @Test
    void generateDeadline_ruleCodeChanged_oneToOne_reusesExistingViaPositionalFallback() {
        Court court = court();
        CaseEvent event = event(case_(court));
        DeadlineRule oldRule = rule(1L, "CODE_OLD");
        DeadlineRule newRule = rule(2L, "CODE_NEW");
        Deadline existing = existingDeadline(event, oldRule, LocalDate.of(2024, 6, 1));
        when(deadlineRuleFinder.findAllActiveRules(any(), any(), any(), any())).thenReturn(List.of(newRule));
        when(deadlineFinder.findAllByTriggeringEvent(event)).thenReturn(List.of(existing));
        stubNoHolidays();
        when(deadlineRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<Deadline> result = deadlineEngine.generateDeadline(event, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(existing.getId());
        assertThat(result.getFirst().getRule()).isEqualTo(newRule);
        verify(deadlineRepository, times(1)).save(any());
    }

    // ─── AC3: ручний дедлайн на тій самій події ніколи не бере участі в реконсиляції ─────

    @Test
    void generateDeadline_manualDeadlineOnSameEvent_isExcludedFromReconciliation_andNeverMutated() {
        Court court = court();
        CaseEvent event = event(case_(court));
        DeadlineRule rule = rule(1L, "CODE_A");
        Deadline manual = Deadline.builder()
                .id(999L)
                .organizationId(event.getOrganizationId())
                .case_(event.getCase_())
                .triggeringEvent(event)
                .rule(null)
                .source(DeadlineSource.MANUAL)
                .title("Ручний строк юриста")
                .startsOn(LocalDate.of(2024, 1, 1))
                .dueOn(LocalDate.of(2024, 1, 15))
                .build();
        when(deadlineRuleFinder.findAllActiveRules(any(), any(), any(), any())).thenReturn(List.of(rule));
        when(deadlineFinder.findAllByTriggeringEvent(event)).thenReturn(List.of(manual));
        stubNoHolidays();
        stubSaveAssignsId();

        List<Deadline> result = deadlineEngine.generateDeadline(event, 1L);

        // Без фільтра source==RULE у reconcile() позиційний fallback підхопив би manual як
        // "вільний слот" і переписав би його title/rule/dueOn під ruleA - замість цього тут
        // мусить з'явитись ДРУГИЙ, окремий рядок, а ручний лишитись незмінним.
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isNotEqualTo(manual.getId());
        assertThat(result.getFirst().getRule()).isEqualTo(rule);
        assertThat(manual.getSource()).isEqualTo(DeadlineSource.MANUAL);
        assertThat(manual.getRule()).isNull();
        assertThat(manual.getTitle()).isEqualTo("Ручний строк юриста");
        assertThat(manual.getDueOn()).isEqualTo(LocalDate.of(2024, 1, 15));
        verify(deadlineRepository, never()).save(manual);
    }

    // ─── AC5: гонка на uq_deadlines_triggering_event_rule ───────────────────

    @Test
    void generateDeadline_uniqueConstraintRace_recoversRowInsertedByOtherTransaction() throws SQLException {
        Court court = court();
        CaseEvent event = event(case_(court));
        DeadlineRule rule = rule(1L, "CODE_A");
        Deadline winningRow = existingDeadline(event, rule, EXPECTED_DUE_ON);
        SQLException sqlException = new SQLException(
                "ERROR: duplicate key value violates unique constraint \"uq_deadlines_triggering_event_rule\"");
        when(deadlineRuleFinder.findAllActiveRules(any(), any(), any(), any())).thenReturn(List.of(rule));
        when(deadlineFinder.findAllByTriggeringEvent(event)).thenReturn(List.of());
        when(deadlineFinder.findByTriggeringEventAndRule(event, rule)).thenReturn(Optional.of(winningRow));
        stubNoHolidays();
        when(deadlineRepository.save(any())).thenThrow(new DataIntegrityViolationException("conflict", sqlException));

        List<Deadline> result = deadlineEngine.generateDeadline(event, 1L);

        assertThat(result).containsExactly(winningRow);
    }

    @Test
    void generateDeadline_unrelatedConstraintViolation_propagates_insteadOfSwallowing() throws SQLException {
        Court court = court();
        CaseEvent event = event(case_(court));
        DeadlineRule rule = rule(1L, "CODE_A");
        SQLException sqlException = new SQLException("ERROR: violates check constraint \"some_other_check\"");
        when(deadlineRuleFinder.findAllActiveRules(any(), any(), any(), any())).thenReturn(List.of(rule));
        when(deadlineFinder.findAllByTriggeringEvent(event)).thenReturn(List.of());
        stubNoHolidays();
        when(deadlineRepository.save(any())).thenThrow(new DataIntegrityViolationException("conflict", sqlException));

        assertThatThrownBy(() -> deadlineEngine.generateDeadline(event, 1L))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(deadlineFinder, never()).findByTriggeringEventAndRule(any(), any());
    }

    // ─── орфановані дедлайни (правил стало менше, ніж уже наявних дедлайнів) ────────────

    // Раніше такий рядок лишався PENDING з попередженням лише в лог - юрист бачив у картці
    // справи строк, що вже нічому не відповідає, без жодного видимого сигналу про це.
    @Test
    void generateDeadline_moreExistingThanNewRules_autoRejectsOrphaned_withHumanActor() {
        Court court = court();
        CaseEvent event = event(case_(court));
        DeadlineRule oldRuleA = rule(1L, "CODE_OLD_A");
        DeadlineRule oldRuleB = rule(2L, "CODE_OLD_B");
        DeadlineRule newRule = rule(3L, "CODE_NEW");
        Deadline existingA = existingDeadline(event, oldRuleA, LocalDate.of(2024, 6, 1));
        Deadline existingB = existingDeadline(event, oldRuleB, LocalDate.of(2024, 6, 1));
        when(deadlineRuleFinder.findAllActiveRules(any(), any(), any(), any())).thenReturn(List.of(newRule));
        when(deadlineFinder.findAllByTriggeringEvent(event)).thenReturn(List.of(existingA, existingB));
        stubNoHolidays();
        when(deadlineRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<Deadline> result = deadlineEngine.generateDeadline(event, 7L);

        // Один рядок (перший за id) переприв'язується до newRule позиційним fallback-ом, другий
        // - автоматично відхиляється, а не лишається мовчки PENDING.
        assertThat(result).hasSize(2);
        Deadline reused = result.stream().filter(d -> d.getId().equals(existingA.getId())).findFirst().orElseThrow();
        Deadline orphaned = result.stream().filter(d -> d.getId().equals(existingB.getId())).findFirst().orElseThrow();

        assertThat(reused.getRule()).isEqualTo(newRule);
        assertThat(reused.getStatus()).isEqualTo(DeadlineStatus.PENDING);

        assertThat(orphaned.getStatus()).isEqualTo(DeadlineStatus.REJECTED);
        assertThat(orphaned.getRejectedAt()).isNotNull();
        assertThat(orphaned.getRejectedBy()).isEqualTo(7L);
        assertThat(orphaned.getRejectionReason()).isNotBlank();
        // Правило старого (тепер відхиленого) рядка НЕ переписується - лишається доказом, під
        // яким правилом строк рахувався до того, як його автоматично відхилили.
        assertThat(orphaned.getRule()).isEqualTo(oldRuleB);

        verify(auditLogService)
                .log(any(), any(), any(), any(), org.mockito.ArgumentMatchers.eq("status"), org.mockito.ArgumentMatchers.eq("PENDING"), org.mockito.ArgumentMatchers.eq("REJECTED"));
    }

    // changedBy == null (напр. перерахунок від DeadlineListener при зміні виробничого календаря) -
    // той самий факт відхилення, але через logSystemChange, а не вигаданого системного юзера.
    @Test
    void generateDeadline_orphanedWithoutHumanActor_usesSystemAuditTrail() {
        Court court = court();
        CaseEvent event = event(case_(court));
        DeadlineRule oldRuleA = rule(1L, "CODE_OLD_A");
        DeadlineRule oldRuleB = rule(2L, "CODE_OLD_B");
        DeadlineRule newRule = rule(3L, "CODE_NEW");
        Deadline existingA = existingDeadline(event, oldRuleA, LocalDate.of(2024, 6, 1));
        Deadline existingB = existingDeadline(event, oldRuleB, LocalDate.of(2024, 6, 1));
        when(deadlineRuleFinder.findAllActiveRules(any(), any(), any(), any())).thenReturn(List.of(newRule));
        when(deadlineFinder.findAllByTriggeringEvent(event)).thenReturn(List.of(existingA, existingB));
        stubNoHolidays();
        when(deadlineRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<Deadline> result = deadlineEngine.generateDeadline(event, null);

        Deadline orphaned = result.stream().filter(d -> d.getId().equals(existingB.getId())).findFirst().orElseThrow();
        assertThat(orphaned.getStatus()).isEqualTo(DeadlineStatus.REJECTED);
        assertThat(orphaned.getRejectedBy()).isNull();

        verify(auditLogService)
                .logSystemChange(any(), any(), any(), org.mockito.ArgumentMatchers.eq("status"), org.mockito.ArgumentMatchers.eq("PENDING"), org.mockito.ArgumentMatchers.eq("REJECTED"));
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any(), any());
    }
}

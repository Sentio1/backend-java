package com.lisovskyi.core_service.deadline;

import com.lisovskyi.core_service.case_event.CaseEvent;
import com.lisovskyi.core_service.deadline.enums.DeadlineStatus;
import com.lisovskyi.core_service.deadline_rule.DeadlineRule;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DeadlineRepository extends JpaRepository<Deadline, Long> {

    // SEN-29: подія може породжувати кілька дедлайнів (по одному на кожне застосовне правило),
    // тож "дедлайн(и) цієї події" - завжди список, ніколи Optional (findByTriggeringEvent/
    // findIdByTriggeringEventId прибрані разом з переходом DeadlineEngine на
    // findAllActiveRules - одна подія й один рядок Deadline більше не 1:1).
    List<Deadline> findAllByTriggeringEvent(CaseEvent triggeringEvent);

    // Для update-в-місці одного конкретного (подія, правило) - на відміну від пошуку "чи є вже
    // хоч якийсь дедлайн для цієї події" (findAllByTriggeringEvent), тут потрібен саме цей
    // рядок: і для звичайного recalculation-шляху (SEN-29 reconciliation по DeadlineRule.code -
    // DeadlineEngine), і для відновлення після гонки на uq_deadlines_triggering_event_rule.
    Optional<Deadline> findByTriggeringEventAndRule(CaseEvent triggeringEvent, DeadlineRule rule);

    @Query("SELECT d FROM Deadline d " + "WHERE d.id = :id "
            + "AND d.case_.id = :caseId "
            + "AND d.organizationId = :organizationId")
    Optional<Deadline> findByIdAndCaseIdAndOrganizationId(
            @Param("id") Long id, @Param("caseId") Long caseId, @Param("organizationId") Long organizationId);

    List<Deadline> findAllByTriggeringEventIdIn(List<Long> triggeringEventIds);

    @Query("SELECT d FROM Deadline d " + "WHERE d.case_.id = :caseId "
            + "AND d.organizationId = :organizationId "
            + "ORDER BY d.dueOn")
    Page<Deadline> findAllByCaseIdAndOrganizationId(
            @Param("caseId") Long caseId, @Param("organizationId") Long organizationId, Pageable pageable);

    @Query("SELECT d FROM Deadline d " + "WHERE d.triggeringEvent.id = :triggeringEventId "
            + "AND d.case_.id = :caseId "
            + "AND d.organizationId = :organizationId "
            + "ORDER BY d.dueOn")
    Page<Deadline> findAllByTriggeringEventIdAndCaseIdAndOrganizationId(
            @Param("triggeringEventId") Long triggeringEventId,
            @Param("caseId") Long caseId,
            @Param("organizationId") Long organizationId,
            Pageable pageable);

    boolean existsByRuleId(Long ruleId);

    // SEN-26: календар змінився на конкретну дату - потрібні CaseEvent-и всіх ще НЕ
    // вирішених (PENDING) дедлайнів, чиє вікно [startsOn, dueOn] цю дату накриває, щоб
    // прогнати їх через DeadlineGenerator.recalcAllDeadlines. DONE/MISSED навмисно
    // виключені - це вже зафіксовані факти, зміна календаря заднім числом їх не чіпає.
    @Query("SELECT ce FROM Deadline d JOIN d.triggeringEvent ce "
            + "WHERE d.status = :status "
            + "AND d.startsOn <= :date "
            + "AND d.dueOn >= :date "
            + "AND d.source <> 'MANUAL'")
    List<CaseEvent> findTriggeringEventsByStatusAndWindowCovering(
            @Param("status") DeadlineStatus status, @Param("date") LocalDate date);
}

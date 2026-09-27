package com.sentio.core_service.deadline.internal.repository;

import com.sentio.core_service.deadline.internal.model.Deadline;

import com.sentio.core_service.deadline.internal.enums.DeadlineStatus;
import com.sentio.core_service.deadline.internal.model.DeadlineRule;

import java.time.LocalDate;
import java.util.Collection;
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
    // тож "дедлайн(и) цієї події" - завжди список, ніколи Optional.
    List<Deadline> findAllByTriggeringEventId(Long triggeringEventId);

    // Для update-в-місці одного конкретного (подія, правило) - і для звичайного
    // recalculation-шляху (SEN-29 reconciliation по DeadlineRule.code - DeadlineEngine), і для
    // відновлення після гонки на uq_deadlines_triggering_event_rule.
    Optional<Deadline> findByTriggeringEventIdAndRule(Long triggeringEventId, DeadlineRule rule);

    Optional<Deadline> findByIdAndCaseIdAndOrganizationId(Long id, Long caseId, Long organizationId);

    List<Deadline> findAllByTriggeringEventIdIn(Collection<Long> triggeringEventIds);

    Page<Deadline> findAllByCaseIdAndOrganizationIdOrderByDueOn(Long caseId, Long organizationId, Pageable pageable);

    Page<Deadline> findAllByTriggeringEventIdAndCaseIdAndOrganizationIdOrderByDueOn(
            Long triggeringEventId, Long caseId, Long organizationId, Pageable pageable);

    @Query("SELECT d.id FROM Deadline d WHERE d.caseId = :caseId AND d.organizationId = :organizationId")
    List<Long> findIdsByCaseIdAndOrganizationId(
            @Param("caseId") Long caseId, @Param("organizationId") Long organizationId);

    boolean existsByRuleId(Long ruleId);

    // SEN-26: календар змінився на конкретну дату - потрібні події-тригери всіх ще НЕ
    // вирішених (PENDING) дедлайнів, чиє вікно [startsOn, dueOn] цю дату накриває, щоб
    // перерахувати їх. DONE/MISSED навмисно виключені - це вже зафіксовані факти, зміна
    // календаря заднім числом їх не чіпає. Лише id: самі події - у модулі litigation.
    @Query("SELECT DISTINCT d.triggeringEventId FROM Deadline d "
            + "WHERE d.status = :status "
            + "AND d.startsOn <= :date "
            + "AND d.dueOn >= :date "
            + "AND d.source <> 'MANUAL' "
            + "AND d.triggeringEventId IS NOT NULL")
    List<Long> findTriggeringEventIdsByStatusAndWindowCovering(
            @Param("status") DeadlineStatus status, @Param("date") LocalDate date);
}

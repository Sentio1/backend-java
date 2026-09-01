package com.lisovskyi.core_service.deadline;

import com.lisovskyi.core_service.case_event.CaseEvent;
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

    Optional<Deadline> findByTriggeringEvent(CaseEvent triggeringEvent);

    Optional<Long> findIdByTriggeringEventId(Long triggeringEventId);

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
}

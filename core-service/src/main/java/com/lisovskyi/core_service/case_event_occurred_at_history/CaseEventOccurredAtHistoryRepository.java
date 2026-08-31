package com.lisovskyi.core_service.case_event_occurred_at_history;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CaseEventOccurredAtHistoryRepository extends JpaRepository<CaseEventOccurredAtHistory, Long> {

    @Query("""
        SELECT h FROM CaseEventOccurredAtHistory h
        WHERE h.caseEvent.id = :caseEventId
          AND h.caseEvent.case_.id = :caseId
          AND h.organizationId = :organizationId
        ORDER BY h.changedAt DESC
        """)
    Page<CaseEventOccurredAtHistory> findAllByCaseEventIdAndCaseIdAndOrganizationId(
            @Param("caseEventId") Long caseEventId, @Param("caseId") Long caseId, @Param("organizationId") Long organizationId, Pageable pageable);
}

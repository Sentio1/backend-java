package com.lisovskyi.core_service.case_event;

import com.lisovskyi.core_service.case_event.enums.EventCode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CaseEventRepository extends JpaRepository<CaseEvent, Long> {

    @Query("""
        SELECT c FROM CaseEvent c
        WHERE c.case_.id = :caseId
          AND c.organizationId = :organizationId
        """)
    List<CaseEvent> findAllByCaseIdAndOrganizationId(
            @Param("caseId") Long caseId, @Param("organizationId") Long organizationId);

    @Query("""
        SELECT c FROM CaseEvent c
        WHERE c.case_.id = :caseId
          AND c.organizationId = :organizationId
          AND (:eventCode IS NULL OR c.eventCode = :eventCode)
        ORDER BY c.occurredAt
        """)
    Page<CaseEvent> findAllByCaseIdAndOrganizationId(
            @Param("caseId") Long caseId,
            @Param("organizationId") Long organizationId,
            @Param("eventCode") EventCode eventCode,
            Pageable pageable);

    @Query("""
        SELECT c FROM CaseEvent c
        WHERE c.case_.id = :caseId
          AND c.organizationId = :organizationId
          AND (:eventCodes IS NULL OR c.eventCode IN :eventCodes)
        ORDER BY c.occurredAt
        """)
    Page<CaseEvent> findAllByCaseIdAndOrganizationId(
            @Param("caseId") Long caseId,
            @Param("organizationId") Long organizationId,
            @Param("eventCodes") List<EventCode> eventCodes,
            Pageable pageable);

    @Query("""
        SELECT c FROM CaseEvent c
        WHERE c.id = :id
          AND c.case_.id = :caseId
          AND c.organizationId = :organizationId
        """)
    Optional<CaseEvent> findByIdAndCaseIdAndOrganizationId(
            @Param("id") Long id, @Param("caseId") Long caseId, @Param("organizationId") Long organizationId);

    @Query(
            value = "SELECT * FROM core.case_events ce " + "WHERE ce.id = :id "
                    + "AND ce.case_id = :caseId "
                    + "AND ce.organization_id = :organizationId "
                    + "AND ce.deleted_at IS NOT NULL",
            nativeQuery = true)
    Optional<CaseEvent> findDeletedByIdAndCaseIdAndOrganizationId(
            @Param("id") Long id, @Param("caseId") Long caseId, @Param("organizationId") Long organizationId);
}

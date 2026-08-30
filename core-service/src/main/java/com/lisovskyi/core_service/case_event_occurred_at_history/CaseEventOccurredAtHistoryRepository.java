package com.lisovskyi.core_service.case_event_occurred_at_history;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CaseEventOccurredAtHistoryRepository extends JpaRepository<CaseEventOccurredAtHistory, Long> {

    // derived-query нейминг тут не працює: шлях case_.id (CaseEvent) проходить
    // через поле з underscore (case_ — "case" зарезервоване слово Java), яке
    // Spring Data не парсить з "CaseId" - той самий патерн, що в CaseEventRepository.
    @Query("""
        SELECT h FROM CaseEventOccurredAtHistory h
        WHERE h.caseEvent.case_.id = :caseId
          AND h.organizationId = :organizationId
        ORDER BY h.changedAt DESC
        """)
    Page<CaseEventOccurredAtHistory> findAllByCaseIdAndOrganizationId(
            @Param("caseId") Long caseId, @Param("organizationId") Long organizationId, Pageable pageable);
}

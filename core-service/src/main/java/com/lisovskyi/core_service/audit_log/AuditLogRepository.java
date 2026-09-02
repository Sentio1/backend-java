package com.lisovskyi.core_service.audit_log;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// Немає update/delete-методів навмисно (AC SEN-23: "записи аудиту не редагуються і не
// видаляються") - але це лише самодисципліна рівня коду, реальна гарантія - тригер
// audit_logs_immutable у V28__audit_log.sql, який блокує UPDATE/DELETE на рівні БД
// незалежно від того, яким шляхом іде запис.
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // "Історія доступна з картки справи" (AC) - одна стрічка на всю справу, а не три окремих
    // виклики по CASE/CASE_EVENT/DEADLINE: entity_id для CASE_EVENT/DEADLINE-рядків - це id
    // самої події/дедлайна, не id справи, тож приналежність до справи для них перевіряється
    // підзапитом через їхній власний зв'язок case_.id, а не прямим entity_id = :caseId.
    @Query(
            """
            SELECT a FROM AuditLog a
            WHERE a.organizationId = :organizationId
            AND (
                (a.entityType = com.lisovskyi.core_service.audit_log.EntityType.CASE AND a.entityId = :caseId)
                OR (a.entityType = com.lisovskyi.core_service.audit_log.EntityType.CASE_EVENT
                    AND a.entityId IN (SELECT ce.id FROM CaseEvent ce WHERE ce.case_.id = :caseId))
                OR (a.entityType = com.lisovskyi.core_service.audit_log.EntityType.DEADLINE
                    AND a.entityId IN (SELECT d.id FROM Deadline d WHERE d.case_.id = :caseId))
            )
            ORDER BY a.changedAt DESC
            """)
    Page<AuditLog> findAllByCaseIdAndOrganizationId(
            @Param("caseId") Long caseId, @Param("organizationId") Long organizationId, Pageable pageable);
}

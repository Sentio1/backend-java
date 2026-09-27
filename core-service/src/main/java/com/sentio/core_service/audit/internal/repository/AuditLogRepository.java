package com.sentio.core_service.audit.internal.repository;

import com.sentio.core_service.audit.internal.model.AuditLog;

import java.util.Collection;
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
    // самої події/дедлайна, не id справи. Які саме події/дедлайни належать справі, знає не
    // цей модуль, а власник справ - він і передає їхні id (див. AuditLogService.findCaseHistory).
    @Query(
            """
            SELECT a FROM AuditLog a
            WHERE a.organizationId = :organizationId
            AND (
                (a.entityType = com.sentio.core_service.audit.api.enums.EntityType.CASE AND a.entityId = :caseId)
                OR (a.entityType = com.sentio.core_service.audit.api.enums.EntityType.CASE_EVENT
                    AND a.entityId IN :caseEventIds)
                OR (a.entityType = com.sentio.core_service.audit.api.enums.EntityType.DEADLINE
                    AND a.entityId IN :deadlineIds)
            )
            ORDER BY a.changedAt DESC
            """)
    Page<AuditLog> findCaseHistory(
            @Param("organizationId") long organizationId,
            @Param("caseId") long caseId,
            @Param("caseEventIds") Collection<Long> caseEventIds,
            @Param("deadlineIds") Collection<Long> deadlineIds,
            Pageable pageable);
}

package com.lisovskyi.core_service.audit_log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lisovskyi.core_service.TestcontainersConfiguration;
import com.lisovskyi.core_service.audit_log.enums.ChangedByType;
import com.lisovskyi.core_service.audit_log.enums.EntityType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

// SEN-23 AC: "Записи аудиту не редагуються і не видаляються" - формалізує перевірку, яку раніше
// робив разово вручну (голий Postgres + docker exec) для V28__audit_log.sql: тригер
// audit_logs_immutable блокує і UPDATE, і DELETE на рівні БД, незалежно від шляху виклику
// (працює навіть для суперюзера з локального docker-compose, на відміну від REVOKE).
//
// UPDATE і DELETE - окремі тести, не один: Postgres переводить транзакцію в aborted-стан після
// першого ж SQL-винятку - другий native-запит у тій самій транзакції впав би з "current
// transaction is aborted", а не з очікуваною помилкою тригера.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class AuditLogImmutabilityIT {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private EntityManager entityManager;

    private Long persistAuditLogRow() {
        AuditLog auditLog = auditLogRepository.save(AuditLog.builder()
                .organizationId(1L)
                .entityType(EntityType.CASE)
                .entityId(1L)
                .fieldName("title")
                .oldValue("old")
                .newValue("new")
                .changedBy(1L)
                .changedByType(ChangedByType.USER)
                .changedAt(Instant.now())
                .build());
        entityManager.flush();
        return auditLog.getId();
    }

    @Test
    void updatingAuditLogRow_isRejectedByDbTrigger() {
        Long id = persistAuditLogRow();

        assertThatThrownBy(() -> entityManager
                        .createNativeQuery("UPDATE core.audit_logs SET new_value = 'hacked' WHERE id = :id")
                        .setParameter("id", id)
                        .executeUpdate())
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void deletingAuditLogRow_isRejectedByDbTrigger() {
        Long id = persistAuditLogRow();

        assertThatThrownBy(() -> entityManager
                        .createNativeQuery("DELETE FROM core.audit_logs WHERE id = :id")
                        .setParameter("id", id)
                        .executeUpdate())
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void insertingAuditLogRow_isStillAllowed() {
        Long id = persistAuditLogRow();

        assertThat(auditLogRepository.findById(id)).isPresent();
    }
}

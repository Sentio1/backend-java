package com.lisovskyi.core_service.audit_log;

import com.lisovskyi.core_service.audit_log.enums.ChangedByType;
import com.lisovskyi.core_service.audit_log.enums.EntityType;
import com.lisovskyi.jpa.autoconfigure.entity.CreationTimestampedEntity;
import com.lisovskyi.jpa.autoconfigure.generator.SequenceSize;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@SequenceSize(size = 50)
public class AuditLog extends CreationTimestampedEntity {

    @Column(name = "organization_id", nullable = false)
    private long organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private EntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private long entityId;

    @Column(name = "field_name", nullable = false)
    private String fieldName;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    // Nullable: тільки для changedByType == USER це реальний auth.users.id (soft-ref, як і
    // раніше); для SYSTEM - null, і саме changedByType, а не вигаданий id, каже, чому.
    @Column(name = "changed_by")
    private Long changedBy;

    // USER / SYSTEM - хто відповідає за зміну: людина (тоді changedBy обов'язковий) чи
    // автоматичний процес (тоді changedBy = null). БД (V35, CHECK-обмеження
    // audit_logs_changed_by_matches_type) гарантує, що ці два поля не розійдуться.
    @Enumerated(EnumType.STRING)
    @Column(name = "changed_by_type", nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ChangedByType changedByType;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;
}

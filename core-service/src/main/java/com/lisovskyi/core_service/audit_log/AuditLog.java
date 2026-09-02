package com.lisovskyi.core_service.audit_log;

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

    @Column(name = "changed_by", nullable = false)
    private long changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;
}

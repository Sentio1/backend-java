package com.lisovskyi.core_service.entity;

import com.lisovskyi.jpa.autoconfigure.entity.CreationTimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

@MappedSuperclass
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@SQLRestriction("deleted_at IS NULL")
public abstract class CoreEntityCreatedOnly extends CreationTimestampedEntity implements SoftDeleteEntity {

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "delete_reason", length = CoreEntityConstants.DELETE_REASON_LENGTH)
    private String deleteReason;

    @Column(name = "restored_at")
    private Instant restoredAt;

    @Column(name = "restored_by")
    private Long restoredBy;
}

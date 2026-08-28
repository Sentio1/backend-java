package com.sentio.shared.entity;

import com.lisovskyi.jpa.autoconfigure.entity.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

@MappedSuperclass
@SQLRestriction("deleted_at IS NULL")
public abstract class EntityWithRestrictionForDeleted extends TimestampedEntity {

    @Column(name = "deleted_at")
    private Instant deletedAt;
}

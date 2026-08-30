package com.lisovskyi.core_service.entity;

import java.time.Instant;

public interface SoftDeletable {

    default void deleteEntity(SoftDeleteEntity entity, long deletedById, String deleteReason) {
        entity.setRestoredAt(null);
        entity.setRestoredBy(null);

        entity.setDeletedAt(Instant.now());
        entity.setDeletedBy(deletedById);
        entity.setDeleteReason(deleteReason);
    }

    default void restoreEntity(SoftDeleteEntity entity, long restoredById) {
        entity.setDeletedAt(null);
        entity.setDeletedBy(null);
        entity.setDeleteReason(null);

        entity.setRestoredAt(Instant.now());
        entity.setRestoredBy(restoredById);
    }
}

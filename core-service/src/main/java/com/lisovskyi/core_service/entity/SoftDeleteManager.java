package com.lisovskyi.core_service.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SoftDeleteManager {

    public <E extends SoftDeleteEntity> void deleteEntity(E entity, long deletedById, String deleteReason) {
        entity.setRestoredAt(null);
        entity.setRestoredBy(null);

        entity.setDeletedAt(Instant.now());
        entity.setDeletedBy(deletedById);
        entity.setDeleteReason(deleteReason);
    }

    public <E extends SoftDeleteEntity> void deleteEntity(E entity, JpaRepository<E, ?> repository, long deletedById, String deleteReason) {
        deleteEntity(entity, deletedById, deleteReason);
        repository.save(entity);
    }

    public void restoreEntity(SoftDeleteEntity entity, long restoredById) {
        entity.setDeletedAt(null);
        entity.setDeletedBy(null);
        entity.setDeleteReason(null);

        entity.setRestoredAt(Instant.now());
        entity.setRestoredBy(restoredById);
    }

    public <E extends SoftDeleteEntity> void restoreEntity(E entity, JpaRepository<E, ?> repository, long deletedById) {
        restoreEntity(entity, deletedById);
        repository.save(entity);
    }
}

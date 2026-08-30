package com.lisovskyi.core_service.entity;

import java.time.Instant;

public interface SoftDeleteEntity {
    void setDeletedAt(Instant deletedAt);

    void setDeletedBy(Long deletedBy);

    void setDeleteReason(String deleteReason);

    void setRestoredAt(Instant restoredAt);

    void setRestoredBy(Long restoredBy);
}

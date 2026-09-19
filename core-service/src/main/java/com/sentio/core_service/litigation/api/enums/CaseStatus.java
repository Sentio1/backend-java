package com.sentio.core_service.litigation.api.enums;

public enum CaseStatus {
    ACTIVE,
    SUSPENDED,
    COMPLETED,
    ARCHIVED;

    public boolean isTerminal() {
        return this == COMPLETED || this == ARCHIVED;
    }
}

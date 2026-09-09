package com.lisovskyi.core_service.case_.enums;

public enum CaseStatus {
    ACTIVE,
    SUSPENDED,
    COMPLETED,
    ARCHIVED;

    public boolean isTerminal() {
        return this == COMPLETED || this == ARCHIVED;
    }
}

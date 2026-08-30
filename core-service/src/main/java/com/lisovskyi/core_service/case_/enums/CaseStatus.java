package com.lisovskyi.core_service.case_.enums;

public enum CaseStatus {
    DRAFT,
    PRE_TRIAL,
    FIRST_INSTANCE,
    APPEAL,
    CASSATION,
    ENFORCEMENT,
    CLOSED,
    ARCHIVED;

    public boolean isTerminal() {
        return this == CLOSED || this == ARCHIVED;
    }
}

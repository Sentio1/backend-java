package com.lisovskyi.core_service.deadline.enums;

public enum DeadlineStatus {
    PENDING,
    DONE,
    MISSED,
    SUSPENDED,
    EXTENDED,
    // юрист відхилив автоматично створений строк (SEN-29 AC4) - reject{ed,ionReason} на Deadline
    // заповнені, сам факт і причина лишаються в audit_log, подія-тригер нікуди не зникає.
    REJECTED
}

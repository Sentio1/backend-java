package com.sentio.core_service.litigation.api.spi;

/** What a case event needs to know about one of the deadlines it triggered. */
public record CaseEventDeadline(long id, boolean rejected) {}

package com.sentio.core_service.deadline.internal.engine;

import lombok.Builder;

import java.time.LocalDate;

@Builder
public record DeadlineDatesResponse(LocalDate naiveDueOn, LocalDate dueOn) {}

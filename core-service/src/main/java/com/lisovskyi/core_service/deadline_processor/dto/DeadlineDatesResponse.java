package com.lisovskyi.core_service.deadline_processor.dto;

import lombok.Builder;

import java.time.LocalDate;

@Builder
public record DeadlineDatesResponse(LocalDate naiveDueOn, LocalDate dueOn) {}

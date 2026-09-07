package com.lisovskyi.core_service.holiday.dto.event;

import java.time.LocalDate;

public record HolidayChangedEvent(LocalDate date) {}

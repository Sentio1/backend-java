package com.sentio.core_service.calendar.api.event;

import java.time.LocalDate;

public record HolidayChangedEvent(LocalDate date) {}

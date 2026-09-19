package com.sentio.core_service.calendar.internal.exception;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import java.time.LocalDate;

public class HolidayNotFoundException extends ResourceNotFoundException {

    public HolidayNotFoundException(LocalDate date) {
        super("Holiday", "date", date);
    }
}

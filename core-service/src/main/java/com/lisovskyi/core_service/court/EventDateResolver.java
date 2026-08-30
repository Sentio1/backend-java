package com.lisovskyi.core_service.court;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public final class EventDateResolver {

    private EventDateResolver() {
        throw new UnsupportedOperationException();
    }

    public static LocalDate toLocalDate(Instant occurredAt, Court court) {
        ZoneId currentZoneId = ZoneId.of(court.getTimeZone());
        return occurredAt.atZone(currentZoneId).toLocalDate();
    }

    public static ZoneId toZoneId(Court court) {
        return ZoneId.of(court.getTimeZone());
    }
}

package com.sentio.core_service.court.api;

import com.sentio.core_service.court.api.dto.CourtResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public final class EventDateResolver {

    private EventDateResolver() {
        throw new UnsupportedOperationException();
    }

    public static LocalDate toLocalDate(Instant occurredAt, CourtResponse court) {
        ZoneId currentZoneId = ZoneId.of(court.timeZone());
        return occurredAt.atZone(currentZoneId).toLocalDate();
    }

    public static ZoneId toZoneId(CourtResponse court) {
        return ZoneId.of(court.timeZone());
    }
}

package com.sentio.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.web.servlet.DispatcherServlet;

import java.time.DateTimeException;
import java.time.ZoneId;

@ConditionalOnClass(DispatcherServlet.class)
public final class ZoneUtility {

    private ZoneUtility() {
        throw new UnsupportedOperationException();
    }

    public static final String TIMEZONE_HEADER = "X-Timezone";
    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Kyiv");

    public static ZoneId getZone(final HttpServletRequest request) {
        if (request == null) {
            return DEFAULT_ZONE;
        }

        final String timeZoneHeader = request.getHeader(TIMEZONE_HEADER);
        if (timeZoneHeader == null || timeZoneHeader.isBlank()) {
            return DEFAULT_ZONE;
        }

        try {
            return ZoneId.of(timeZoneHeader);
        } catch (DateTimeException _) {
            return DEFAULT_ZONE;
        }
    }
}

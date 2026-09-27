package com.sentio.core_service.court.api.dto;

import com.sentio.core_service.court.api.enums.CourtInstance;

public record CourtResponse(
        long id,
        String name,
        String code,
        CourtInstance courtInstance,
        String region,
        String timeZone,
        String address,
        boolean isActive
) {}

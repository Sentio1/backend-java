package com.lisovskyi.core_service.court.dto.response;

import com.lisovskyi.core_service.court.CourtInstance;

public record CourtResponse(
        Long id,
        String name,
        String code,
        CourtInstance courtInstance,
        String region,
        String timeZone,
        String address,
        boolean isActive) {}

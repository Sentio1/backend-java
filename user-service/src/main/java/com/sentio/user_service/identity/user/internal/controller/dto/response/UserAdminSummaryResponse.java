package com.sentio.user_service.identity.user.internal.controller.dto.response;

import com.sentio.user_service.identity.user.api.enums.PlatformRole;
import java.time.Instant;
import lombok.Builder;

@Builder
public record UserAdminSummaryResponse(
        long id,
        String email,
        String lastName,
        String firstName,
        PlatformRole platformRole,
        int organizationCount,
        Instant createdAt,
        Instant deletedAt
) {}

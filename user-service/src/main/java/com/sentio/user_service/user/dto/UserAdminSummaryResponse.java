package com.sentio.user_service.user.dto;

import com.sentio.user_service.user.enums.PlatformRole;
import java.time.Instant;
import lombok.Builder;

@Builder
/** UserAdminSummaryResponse record. */
public record UserAdminSummaryResponse(
        long id,
        String email,
        String lastName,
        String firstName,
        PlatformRole platformRole,
        int organizationCount,
        Instant createdAt,
        Instant deletedAt) {}

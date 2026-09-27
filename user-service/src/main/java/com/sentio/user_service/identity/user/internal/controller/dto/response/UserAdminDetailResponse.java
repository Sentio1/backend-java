package com.sentio.user_service.identity.user.internal.controller.dto.response;

import com.sentio.user_service.identity.organization.api.dto.OrganizationMemberResponse;
import com.sentio.user_service.identity.user.api.enums.PlatformRole;
import java.time.Instant;
import java.util.List;

public record UserAdminDetailResponse(
        long id,
        String email,
        String lastName,
        String firstName,
        String middleName,
        String phoneNumber,
        PlatformRole platformRole,
        Instant createdAt,
        Instant deletedAt,
        List<OrganizationMemberResponse> organizations
) {}

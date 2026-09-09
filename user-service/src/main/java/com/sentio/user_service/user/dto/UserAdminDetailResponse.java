package com.sentio.user_service.user.dto;

import com.sentio.user_service.organization.dto.organization_member.OrganizationMemberResponse;
import com.sentio.user_service.user.enums.PlatformRole;
import java.time.Instant;
import java.util.List;

/** UserAdminDetailResponse record. */
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
        List<OrganizationMemberResponse> organizations) {}

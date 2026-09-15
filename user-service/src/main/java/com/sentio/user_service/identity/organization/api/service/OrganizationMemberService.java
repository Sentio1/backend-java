package com.sentio.user_service.identity.organization.api.service;

import com.sentio.user_service.identity.organization.api.dto.OrganizationMemberDto;
import com.sentio.user_service.identity.organization.api.dto.OrganizationMemberResponse;
import com.sentio.user_service.identity.organization.api.enums.OrgRole;

import java.util.List;
import java.util.Optional;

public interface OrganizationMemberService {

    List<OrganizationMemberResponse> findAllByUserId(long userId);

    Optional<OrganizationMemberDto> findByUserIdAndOrganizationId(long userId, long orgId);

    // No default membership is a legitimate state, not just transiently during Google
    // sign-up - registration is always org-less (see AuthService.register), so
    // login/refresh have to tolerate it too, not just Google's fallback path.
    Optional<OrganizationMemberDto> findDefaultMembership(long userId);

    void lockOrganizationOrThrow(long orgId);

    long countByOrganizationIdAndRole(long orgId, OrgRole role);

    int countByUserId(long userId);

    void deleteAllByUserId(long userId);
}

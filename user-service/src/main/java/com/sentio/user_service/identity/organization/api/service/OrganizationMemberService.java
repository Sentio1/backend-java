package com.sentio.user_service.identity.organization.api.service;

import com.sentio.user_service.identity.organization.api.dto.OrganizationMemberDto;
import com.sentio.user_service.identity.organization.api.dto.OrganizationMemberResponse;
import com.sentio.user_service.identity.organization.api.enums.OrgRole;

import java.util.List;
import java.util.Optional;

public interface OrganizationMemberService {

    List<OrganizationMemberResponse> findAllByUserId(Long userId);

    Optional<OrganizationMemberDto> findByUserIdAndOrganizationId(Long userId, Long orgId);

    // No default membership is a legitimate state, not just transiently during Google
    // sign-up - registration is always org-less (see AuthService.register), so
    // login/refresh have to tolerate it too, not just Google's fallback path.
    Optional<OrganizationMemberDto> findDefaultMembership(Long userId);

    void lockOrganizationOrThrow(Long orgId);

    long countByOrganizationIdAndRole(Long orgId, OrgRole role);

    int countByUserId(Long userId);

    void deleteAllByUserId(Long userId);
}

package com.sentio.user_service.identity.organization.api.service;

import com.sentio.user_service.identity.organization.api.dto.CreateOrganizationRequest;
import com.sentio.user_service.identity.organization.api.dto.OrganizationDto;
import com.sentio.user_service.identity.organization.api.dto.OrganizationMemberDto;

public interface OrganizationService {

    OrganizationDto getOrganizationById(long orgId);

    // Both make the returned membership the caller's default one - the caller is
    // responsible for re-issuing the access token (its org_id/roles claims change).
    OrganizationMemberDto createOrganization(long userId, CreateOrganizationRequest request);

    OrganizationMemberDto switchDefaultOrganization(long userId, long targetOrgId);
}

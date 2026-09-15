package com.sentio.user_service.identity.organization.api.service;

import com.sentio.user_service.identity.organization.api.dto.OrganizationDto;

public interface OrganizationService {

    OrganizationDto getOrganizationById(long orgId);
}

package com.sentio.user_service.identity.organization.api.service;

import com.sentio.user_service.identity.organization.api.dto.OrganizationInviteAcceptResponse;
import com.sentio.user_service.identity.organization.api.dto.OrganizationInviteResponse;

import java.util.List;

public interface OrganizationInviteService {

    List<OrganizationInviteResponse> findAllByEmail(String email);

    OrganizationInviteAcceptResponse acceptInvite(String token, long userId);
}

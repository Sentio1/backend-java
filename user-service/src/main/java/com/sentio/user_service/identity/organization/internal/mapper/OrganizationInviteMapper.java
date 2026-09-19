package com.sentio.user_service.identity.organization.internal.mapper;

import com.sentio.user_service.identity.organization.api.dto.OrganizationInviteAcceptResponse;
import com.sentio.user_service.identity.organization.api.dto.OrganizationInviteResponse;
import com.sentio.user_service.identity.organization.internal.controller.dto.organization_invite.OrganizationInviteCreatedResponse;
import com.sentio.user_service.identity.organization.internal.entity.OrganizationInvite;
import com.sentio.user_service.identity.organization.internal.entity.OrganizationMember;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface OrganizationInviteMapper {

    @Mapping(target = "orgId", source = "organization.id")
    OrganizationInviteResponse toResponse(OrganizationInvite organizationInvite);

    @Mapping(target = "orgId", source = "organizationInvite.organization.id")
    OrganizationInviteCreatedResponse toCreatedResponse(OrganizationInvite organizationInvite, String token);

    @Mapping(target = "orgId", source = "organization.id")
    @Mapping(target = "organizationName", source = "organization.name")
    @Mapping(target = "isDefault", expression = "java(organizationMember.isDefault())")
    OrganizationInviteAcceptResponse toAcceptResponse(OrganizationMember organizationMember);
}

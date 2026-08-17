package com.sentio.user_service.organization.mapper;

import com.sentio.user_service.organization.dto.organization_invite.OrganizationInviteAcceptResponse;
import com.sentio.user_service.organization.dto.organization_invite.OrganizationInviteCreatedResponse;
import com.sentio.user_service.organization.dto.organization_invite.OrganizationInviteResponse;
import com.sentio.user_service.organization.entity.OrganizationInvite;
import com.sentio.user_service.organization.entity.OrganizationMember;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
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

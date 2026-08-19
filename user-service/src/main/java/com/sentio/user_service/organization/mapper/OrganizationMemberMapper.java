package com.sentio.user_service.organization.mapper;

import com.sentio.user_service.organization.dto.organization_member.OrganizationMemberResponse;
import com.sentio.user_service.organization.entity.OrganizationMember;
import com.sentio.user_service.user.dto.UserContextResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
/** OrganizationMemberMapper interface. */
public interface OrganizationMemberMapper {

    @Mapping(target = "orgId", source = "organization.id")
    @Mapping(target = "isDefault", source = "default")
    @Mapping(target = "user", source = "organizationMember")
    OrganizationMemberResponse toResponse(OrganizationMember organizationMember);

    @Mapping(target = "id", source = "user.id")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "firstName", source = "user.firstName")
    @Mapping(target = "lastName", source = "user.lastName")
    @Mapping(target = "orgName", source = "organization.name")
    @Mapping(target = "orgRole", source = "role")
    UserContextResponse toUserContextResponse(OrganizationMember organizationMember);
}

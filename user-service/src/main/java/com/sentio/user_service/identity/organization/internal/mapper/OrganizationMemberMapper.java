package com.sentio.user_service.identity.organization.internal.mapper;

import com.sentio.user_service.identity.organization.api.dto.OrganizationMemberDto;
import com.sentio.user_service.identity.organization.api.dto.OrganizationMemberResponse;
import com.sentio.user_service.identity.organization.internal.entity.OrganizationMember;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface OrganizationMemberMapper {

    // "user" is deliberately left for the caller to fill in (e.g. via
    // UserService.buildUserContext) - OrganizationMember only has a userId, not a
    // User relation, so this mapper has no data to build a UserContextResponse from.
    @Mapping(target = "orgId", source = "organization.id")
    @Mapping(target = "orgRole", source = "role")
    @Mapping(target = "isDefault", source = "default")
    @Mapping(target = "user", ignore = true)
    OrganizationMemberResponse toResponse(OrganizationMember organizationMember);

    @Mapping(target = "organizationId", source = "organization.id")
    @Mapping(target = "organizationName", source = "organization.name")
    @Mapping(target = "orgRole", source = "role")
    OrganizationMemberDto toDto(OrganizationMember organizationMember);
}

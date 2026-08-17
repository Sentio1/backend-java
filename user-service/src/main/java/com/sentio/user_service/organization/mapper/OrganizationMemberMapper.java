package com.sentio.user_service.organization.mapper;

import com.sentio.user_service.organization.dto.organization_member.OrganizationMemberResponse;
import com.sentio.user_service.organization.entity.OrganizationMember;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface OrganizationMemberMapper {

    OrganizationMemberResponse toResponse(OrganizationMember organizationMember);
}

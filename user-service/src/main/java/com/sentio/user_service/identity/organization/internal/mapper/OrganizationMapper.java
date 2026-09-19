package com.sentio.user_service.identity.organization.internal.mapper;

import com.sentio.user_service.identity.organization.api.dto.OrganizationDto;
import com.sentio.user_service.identity.organization.internal.controller.dto.organization.OrganizationResponse;
import com.sentio.user_service.identity.organization.internal.entity.Organization;
import org.mapstruct.Mapper;

@Mapper
public interface OrganizationMapper {

    OrganizationResponse toResponse(Organization organization);

    OrganizationDto toDto(Organization organization);
}

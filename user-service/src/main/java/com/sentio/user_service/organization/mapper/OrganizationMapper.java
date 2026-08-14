package com.sentio.user_service.organization.mapper;

import com.sentio.user_service.organization.dto.OrganizationResponse;
import com.sentio.user_service.organization.entity.Organization;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface OrganizationMapper {
    OrganizationResponse toResponse(Organization organization);
}

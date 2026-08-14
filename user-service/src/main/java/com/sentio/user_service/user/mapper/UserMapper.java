package com.sentio.user_service.user.mapper;

import com.sentio.user_service.organization.entity.OrganizationMember;
import com.sentio.user_service.user.dto.UserContextResponse;
import com.sentio.user_service.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {
    @Mapping(target = "id", source = "user.id")
    @Mapping(target = "orgName", source = "member.organization.name")
    @Mapping(target = "orgRole", source = "member.role")
    UserContextResponse toResponse(User user, OrganizationMember member);
}

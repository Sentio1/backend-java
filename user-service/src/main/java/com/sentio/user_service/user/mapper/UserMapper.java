package com.sentio.user_service.user.mapper;

import com.sentio.user_service.organization.dto.organization_member.OrganizationMemberResponse;
import com.sentio.user_service.organization.entity.OrganizationMember;
import com.sentio.user_service.user.dto.UserAdminDetailResponse;
import com.sentio.user_service.user.dto.UserAdminSummaryResponse;
import com.sentio.user_service.user.dto.UserContextResponse;
import com.sentio.user_service.user.entity.User;
import com.sentio.user_service.user.entity.UserIdentity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
/** UserMapper interface. */
public interface UserMapper {
    @Mapping(target = "id", source = "user.id")
    @Mapping(target = "orgName", source = "member.organization.name")
    @Mapping(target = "orgRole", source = "member.role")
    UserContextResponse toUserContextResponse(User user, OrganizationMember member);

    @Mapping(target = "id", source = "user.id")
    UserAdminSummaryResponse toUserAdminSummaryResponse(User user, int organizationCount);

    @Mapping(target = "id", source = "user.id")
    UserAdminDetailResponse toUserAdminDetailResponse(User user, List<OrganizationMemberResponse> organizations);

    // The LOCAL provider identity every local-registered user gets alongside their
    // password - providerUserId mirrors the user's own id since there's no external
    // provider sub to key off (unlike Google identities, which key off Google's sub).
    // User and UserIdentity both inherit id/createdAt from the jpa-starter base entity
    // classes - without these two ignores, MapStruct's default same-name mapping copies
    // the USER's own id/createdAt onto the new UserIdentity, which makes Hibernate treat
    // it as an already-persisted (detached) entity and refuse to cascade-persist it.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "user", source = "user")
    @Mapping(target = "provider", constant = "LOCAL")
    @Mapping(target = "providerUserId", expression = "java(String.valueOf(user.getId()))")
    UserIdentity toLocalIdentity(User user);
}

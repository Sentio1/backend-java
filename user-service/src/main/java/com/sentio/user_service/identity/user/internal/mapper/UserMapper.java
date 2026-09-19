package com.sentio.user_service.identity.user.internal.mapper;

import com.sentio.user_service.identity.organization.api.dto.OrganizationMemberDto;
import com.sentio.user_service.identity.organization.api.dto.OrganizationMemberResponse;
import com.sentio.user_service.identity.user.api.dto.UserContextResponse;
import com.sentio.user_service.identity.user.api.dto.UserDto;
import com.sentio.user_service.identity.user.internal.controller.dto.response.UserAdminDetailResponse;
import com.sentio.user_service.identity.user.internal.controller.dto.response.UserAdminSummaryResponse;
import com.sentio.user_service.identity.user.internal.entity.User;
import com.sentio.user_service.identity.user.internal.entity.UserIdentity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper
public interface UserMapper {
    @Mapping(target = "id", source = "user.id")
    @Mapping(target = "orgName", source = "member.organizationName")
    @Mapping(target = "orgRole", source = "member.orgRole")
    UserContextResponse toUserContextResponse(User user, OrganizationMemberDto member);

    UserDto toDto(User user);

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
    // The user's own id, not the email: ids are never reused, while an email is freed by a soft
    // delete - an email-keyed LOCAL identity would block re-registration on the
    // (provider, provider_user_id) unique index. Needs a persisted user (id assigned).
    @Mapping(target = "providerUserId", expression = "java(String.valueOf(user.getId()))")
    UserIdentity toLocalIdentity(User user);
}

package com.sentio.user_service.identity.user.api.service;

import com.sentio.user_service.identity.organization.api.dto.OrganizationMemberDto;
import com.sentio.user_service.identity.user.api.dto.UserContextResponse;
import com.sentio.user_service.identity.user.api.dto.UserDto;

public interface UserService {

    UserDto findUserById(long userId);

    UserContextResponse buildUserContext(long userId, OrganizationMemberDto membership);
}

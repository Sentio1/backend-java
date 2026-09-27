package com.sentio.user_service.identity.auth.service;

import com.lisovskyi.web.error.autoconfigure.standard.UnauthorizedException;
import com.sentio.user_service.identity.user.api.dto.UserDto;
import com.sentio.user_service.identity.user.api.enums.PlatformRole;
import org.springframework.stereotype.Component;

@Component
public class AuthGuards {

    public void assertNotDeleted(UserDto user) {
        if (user.deleted()) {
            throw new UnauthorizedException("User account has been deleted");
        }
    }

    // Service accounts authenticate only via /auth/service-token (short-lived access
    // token, no session) - never through the interactive login/refresh/OAuth flows.
    public void assertNotServiceAccount(UserDto user, String errorMessage) {
        if (user.platformRole() == PlatformRole.SERVICE) {
            throw new UnauthorizedException(errorMessage);
        }
    }
}

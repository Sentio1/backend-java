package com.sentio.user_service.auth;

import com.lisovskyi.web.error.autoconfigure.standard.UnauthorizedException;
import com.sentio.user_service.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class AuthGuards {
    public void assertNotDeleted(User user, String errorMessage) {
        if (user.isDeleted()) {
            throw new UnauthorizedException(errorMessage);
        }
    }

    public void assertNotDeleted(User user) {
        if (user.isDeleted()) {
            throw new UnauthorizedException("User account has been deleted");
        }
    }
}

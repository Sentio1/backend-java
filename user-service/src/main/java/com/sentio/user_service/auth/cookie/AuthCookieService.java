package com.sentio.user_service.auth.cookie;

import com.lisovskyi.security.autoconfigure.cookie.CookieService;
import com.sentio.user_service.auth.dto.AuthTokens;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthCookieService {

    private final CookieService cookieService;

    public void setCookies(HttpServletResponse response, AuthTokens tokens) {
        cookieService.setAccessTokenCookie(response, tokens.accessToken());
        cookieService.setRefreshTokenCookie(response, tokens.refreshToken());
    }

    public void clearCookies(HttpServletResponse response) {
        cookieService.clearAccessTokenCookie(response);
        cookieService.clearRefreshTokenCookie(response);
    }
}

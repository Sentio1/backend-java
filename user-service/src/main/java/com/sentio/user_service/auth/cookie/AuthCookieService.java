package com.sentio.user_service.auth.cookie;

import com.lisovskyi.security.autoconfigure.cookie.CookieService;
import com.sentio.user_service.auth.dto.response.AuthTokens;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthCookieService {

    private final CookieService cookieService;

    public void setCookies(HttpServletResponse response, AuthTokens tokens) {
        log.debug("Setting access and refresh token cookies");
        cookieService.setAccessTokenCookie(response, tokens.accessToken());
        cookieService.setRefreshTokenCookie(response, tokens.refreshToken());
    }

    public void clearCookies(HttpServletResponse response) {
        log.debug("Clearing access and refresh token cookies");
        cookieService.clearAccessTokenCookie(response);
        cookieService.clearRefreshTokenCookie(response);
    }
}

package com.sentio.user_service.identity.auth.cookie;

import com.lisovskyi.security.autoconfigure.cookie.CookieService;
import com.sentio.user_service.identity.auth.dto.response.AuthTokens;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class AuthCookieService {

    private final CookieService cookieService;

    public void setCookies(final HttpServletResponse response, AuthTokens tokens) {
        log.debug("Setting access and refresh token cookies");
        cookieService.setAccessTokenCookie(response, tokens.accessToken());
        cookieService.setRefreshTokenCookie(response, tokens.refreshToken());
    }

    public void clearCookies(final HttpServletResponse response) {
        log.debug("Clearing access and refresh token cookies");
        cookieService.clearAccessTokenCookie(response);
        cookieService.clearRefreshTokenCookie(response);
    }
}

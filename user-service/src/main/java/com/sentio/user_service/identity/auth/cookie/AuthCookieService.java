package com.sentio.user_service.identity.auth.cookie;

import com.lisovskyi.security.autoconfigure.cookie.CookieService;
import com.sentio.user_service.identity.auth.dto.response.AuthTokens;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The single entry point for auth cookies in this service - nothing else should talk to the
 * starter's {@link CookieService} directly, so cookie handling (names, paths, what gets set
 * together) stays in one place.
 */
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

    public void setAccessToken(final HttpServletResponse response, String accessToken) {
        cookieService.setAccessTokenCookie(response, accessToken);
    }

    public void clearCookies(final HttpServletResponse response) {
        log.debug("Clearing access and refresh token cookies");
        cookieService.clearAccessTokenCookie(response);
        cookieService.clearRefreshTokenCookie(response);
    }

    public Optional<String> readAccessToken(final HttpServletRequest request) {
        return cookieService.getAccessTokenCookie(request);
    }

    public Optional<String> readRefreshToken(final HttpServletRequest request) {
        return cookieService.getRefreshTokenCookie(request);
    }
}

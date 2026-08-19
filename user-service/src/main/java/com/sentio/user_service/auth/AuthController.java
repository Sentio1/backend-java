package com.sentio.user_service.auth;

import com.lisovskyi.security.autoconfigure.cookie.CookieService;
import com.lisovskyi.web.error.autoconfigure.standard.UnauthorizedException;
import com.sentio.user_service.auth.cookie.AuthCookieService;
import com.sentio.user_service.auth.dto.*;
import com.sentio.user_service.auth.rate_limiting.RateLimitingService;
import com.sentio.user_service.user.dto.UserContextResponse;
import com.sentio.user_service.util.HttpRequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
/** AuthController class. */
public class AuthController {

    private final AuthService authService;
    private final CookieService cookieService;
    private final RateLimitingService rateLimitingService;

    private final AuthCookieService customAuthCookieService;

    @PostMapping("/register")
    public ResponseEntity<UserContextResponse> register(
            @RequestBody @Valid RegistrationRequest registrationRequest,
            final HttpServletRequest request,
            final HttpServletResponse response) {
        String ip = HttpRequestUtils.getClientIP(request);
        rateLimitingService.checkRegisterLimits(registrationRequest.email(), ip);

        AuthResult registerResult = authService.register(registrationRequest, ip, request.getHeader("User-Agent"));
        AuthTokens tokens = registerResult.authTokens();
        UserContextResponse userContext = registerResult.userContext();

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/users/{id}")
                .buildAndExpand(userContext.id())
                .toUri();

        customAuthCookieService.setCookies(response, tokens);

        return ResponseEntity.created(location).body(userContext);
    }

    @PostMapping("/login")
    public ResponseEntity<UserContextResponse> login(
            @RequestBody @Valid LoginRequest loginRequest,
            final HttpServletRequest request,
            final HttpServletResponse response) {
        String ip = HttpRequestUtils.getClientIP(request);
        rateLimitingService.checkLoginLimits(loginRequest.email(), ip);

        AuthResult loginResult = authService.login(loginRequest, ip, request.getHeader("User-Agent"));
        AuthTokens tokens = loginResult.authTokens();
        UserContextResponse userContext = loginResult.userContext();

        customAuthCookieService.setCookies(response, tokens);

        return ResponseEntity.ok().body(userContext);
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(final HttpServletRequest request, final HttpServletResponse response) {
        String refreshToken = cookieService
                .getRefreshTokenCookie(request)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        AuthTokens tokens = authService.refresh(
                refreshToken, HttpRequestUtils.getClientIP(request), request.getHeader("User-Agent"));
        customAuthCookieService.setCookies(response, tokens);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(final HttpServletRequest request, final HttpServletResponse response) {
        String accessToken = cookieService.getAccessTokenCookie(request).orElse(null);
        String refreshToken = cookieService.getRefreshTokenCookie(request).orElse(null);

        authService.logout(accessToken, refreshToken);
        customAuthCookieService.clearCookies(response);

        return ResponseEntity.noContent().build();
    }
}

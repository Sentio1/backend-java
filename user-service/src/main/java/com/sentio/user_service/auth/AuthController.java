package com.sentio.user_service.auth;

import com.lisovskyi.security.autoconfigure.cookie.CookieService;
import com.lisovskyi.web.error.autoconfigure.standard.UnauthorizedException;
import com.sentio.user_service.auth.dto.*;
import com.sentio.user_service.auth.rate_limiting.RateLimitingService;
import com.sentio.user_service.util.HttpRequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CookieService cookieService;
    private final RateLimitingService rateLimitingService;

    @PostMapping("/register")
    public ResponseEntity<UserContextResponse> register(
            @RequestBody @Valid RegistrationRequest registrationRequest,
            final HttpServletRequest request,
            final HttpServletResponse response
    ) {
        rateLimitingService.checkRegisterLimits(registrationRequest.email(), HttpRequestUtils.getClientIP(request));

        AuthResult registerResult = authService.register(registrationRequest);
        AuthTokens tokens = registerResult.authTokens();
        UserContextResponse userContext = registerResult.userContext();

        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/users/{id}")
                .buildAndExpand(userContext.id())
                .toUri();

        setCookies(response, tokens);

        return ResponseEntity
                .created(location)
                .body(userContext);
    }

    @PostMapping("/login")
    public ResponseEntity<UserContextResponse> login(
            @RequestBody @Valid LoginRequest loginRequest,
            final HttpServletRequest request,
            final HttpServletResponse response
    ) {
        rateLimitingService.checkLoginLimits(loginRequest.email(), HttpRequestUtils.getClientIP(request));

        AuthResult loginResult = authService.login(loginRequest);
        AuthTokens tokens = loginResult.authTokens();
        UserContextResponse userContext = loginResult.userContext();

        setCookies(response, tokens);

        return ResponseEntity
                .ok()
                .body(userContext);
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(
            final HttpServletRequest request,
            final HttpServletResponse response
    ) {
        String refreshToken = cookieService.getRefreshTokenCookie(request)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        AuthTokens tokens = authService.refresh(refreshToken);
        setCookies(response, tokens);

        return ResponseEntity
                .noContent().
                build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            final HttpServletRequest request,
            final HttpServletResponse response
    ) {
        String accessToken = cookieService.getAccessTokenCookie(request)
                        .orElse(null);
        String refreshToken = cookieService.getRefreshTokenCookie(request)
                        .orElse(null);

        authService.logout(accessToken, refreshToken);
        clearCookies(response);

        return ResponseEntity
                .noContent()
                .build();
    }


    private void setCookies(HttpServletResponse response, AuthTokens tokens) {
        cookieService.setAccessTokenCookie(response, tokens.accessToken());
        cookieService.setRefreshTokenCookie(response, tokens.refreshToken());
    }

    private void clearCookies(HttpServletResponse response) {
        cookieService.clearAccessTokenCookie(response);
        cookieService.clearRefreshTokenCookie(response);
    }
}

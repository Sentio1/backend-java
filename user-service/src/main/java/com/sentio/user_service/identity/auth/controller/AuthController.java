package com.sentio.user_service.identity.auth.controller;

import com.lisovskyi.web.error.autoconfigure.standard.UnauthorizedException;
import com.sentio.shared.web.HttpRequestUtils;
import com.sentio.shared.web.LocationUtility;
import com.sentio.shared.web.ZoneUtility;
import com.sentio.user_service.identity.auth.cookie.AuthCookieService;
import com.sentio.user_service.identity.auth.dto.request.LoginRequest;
import com.sentio.user_service.identity.auth.dto.request.RegistrationRequest;
import com.sentio.user_service.identity.auth.dto.request.ServiceTokenRequest;
import com.sentio.user_service.identity.auth.dto.response.AuthResult;
import com.sentio.user_service.identity.auth.dto.response.AuthTokens;
import com.sentio.user_service.identity.auth.dto.response.ServiceTokenResult;
import com.sentio.user_service.identity.auth.rate_limiting.RateLimitingService;
import com.sentio.user_service.identity.auth.service.AuthService;
import com.sentio.user_service.identity.auth.service.ServiceToken;
import com.sentio.user_service.identity.user.api.dto.UserContextResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

import static com.sentio.user_service.identity.auth.AuthConstants.INVALID_REFRESH_TOKEN_MSG;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RateLimitingService rateLimitingService;
    private final ServiceToken serviceToken;

    private final AuthCookieService authCookieService;

    @PostMapping("/register")
    public ResponseEntity<UserContextResponse> register(
            @RequestBody @Valid RegistrationRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, defaultValue = "unknown") String userAgent,
            final HttpServletRequest httpRequest,
            final HttpServletResponse response
    ) {
        String ip = HttpRequestUtils.getClientIP(httpRequest);
        rateLimitingService.checkRegisterLimits(request.email(), ip);

        AuthResult registerResult = authService.register(request, ip, userAgent, ZoneUtility.getZone(httpRequest));
        AuthTokens tokens = registerResult.authTokens();
        UserContextResponse userContext = registerResult.userContext();

        URI location = LocationUtility.buildLocationFromPathTemplate("/users/{id}", userContext.id());

        authCookieService.setCookies(response, tokens);

        return ResponseEntity.created(location).body(userContext);
    }

    @PostMapping("/login")
    public ResponseEntity<UserContextResponse> login(
            @RequestBody @Valid LoginRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, defaultValue = "unknown") String userAgent,
            final HttpServletRequest httpRequest,
            final HttpServletResponse response
    ) {
        String ip = HttpRequestUtils.getClientIP(httpRequest);
        rateLimitingService.checkLoginLimits(request.email(), ip);

        AuthResult loginResult = authService.login(request, ip, userAgent);
        AuthTokens tokens = loginResult.authTokens();
        UserContextResponse userContext = loginResult.userContext();

        authCookieService.setCookies(response, tokens);

        return ResponseEntity.ok(userContext);
    }

    @PostMapping("/service-token")
    public ResponseEntity<ServiceTokenResult> serviceToken(
            @RequestBody @Valid ServiceTokenRequest request,
            final HttpServletRequest httpRequest
    ) {
        rateLimitingService.checkServiceTokenLimits(
                request.clientId(), HttpRequestUtils.getClientIP(httpRequest)
        );

        ServiceTokenResult accessToken = serviceToken.serviceToken(request);
        return ResponseEntity.ok().body(accessToken);
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(
            @RequestHeader(value = HttpHeaders.USER_AGENT, defaultValue = "unknown") String userAgent,
            final HttpServletRequest httpRequest,
            final HttpServletResponse response
    ) {
        rateLimitingService.checkRefreshLimits(HttpRequestUtils.getClientIP(httpRequest));

        String refreshToken = authCookieService
                .readRefreshToken(httpRequest)
                .orElseThrow(() -> new UnauthorizedException(INVALID_REFRESH_TOKEN_MSG));

        AuthTokens tokens = authService.refresh(
                refreshToken, HttpRequestUtils.getClientIP(httpRequest), userAgent
        );
        authCookieService.setCookies(response, tokens);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        final HttpServletRequest httpRequest,
            final HttpServletResponse response
    ) {
        String accessToken = authCookieService.readAccessToken(httpRequest).orElse(null);
        String refreshToken = authCookieService.readRefreshToken(httpRequest).orElse(null);

        authService.logout(accessToken, refreshToken);
        authCookieService.clearCookies(response);

        return ResponseEntity.noContent().build();
    }
}

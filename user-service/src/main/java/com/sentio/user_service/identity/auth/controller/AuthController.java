package com.sentio.user_service.identity.auth.controller;

import com.lisovskyi.web.error.autoconfigure.standard.UnauthorizedException;
import com.sentio.shared.web.HttpRequestUtils;
import com.sentio.shared.web.LocationUtility;
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
            @RequestBody @Valid RegistrationRequest registrationRequest,
            @RequestHeader(value = HttpHeaders.USER_AGENT, defaultValue = "unknown") String userAgent,
            final HttpServletRequest request,
            final HttpServletResponse response
    ) {
        String ip = HttpRequestUtils.getClientIP(request);
        rateLimitingService.checkRegisterLimits(registrationRequest.email(), ip);

        AuthResult registerResult = authService.register(registrationRequest, ip, userAgent);
        AuthTokens tokens = registerResult.authTokens();
        UserContextResponse userContext = registerResult.userContext();

        URI location = LocationUtility.buildLocationFromPathTemplate("/users/{id}", userContext.id());

        authCookieService.setCookies(response, tokens);

        return ResponseEntity.created(location).body(userContext);
    }

    @PostMapping("/login")
    public ResponseEntity<UserContextResponse> login(
            @RequestBody @Valid LoginRequest loginRequest,
            @RequestHeader(value = HttpHeaders.USER_AGENT, defaultValue = "unknown") String userAgent,
            final HttpServletRequest request,
            final HttpServletResponse response
    ) {
        String ip = HttpRequestUtils.getClientIP(request);
        rateLimitingService.checkLoginLimits(loginRequest.email(), ip);

        AuthResult loginResult = authService.login(loginRequest, ip, userAgent);
        AuthTokens tokens = loginResult.authTokens();
        UserContextResponse userContext = loginResult.userContext();

        authCookieService.setCookies(response, tokens);

        return ResponseEntity.ok(userContext);
    }

    @PostMapping("/service-token")
    public ResponseEntity<ServiceTokenResult> serviceToken(
            @RequestBody @Valid ServiceTokenRequest serviceTokenRequest,
            final HttpServletRequest request
    ) {
        rateLimitingService.checkServiceTokenLimits(
                serviceTokenRequest.clientId(), HttpRequestUtils.getClientIP(request)
        );

        ServiceTokenResult accessToken = serviceToken.serviceToken(serviceTokenRequest);
        return ResponseEntity.ok().body(accessToken);
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(
            @RequestHeader(value = HttpHeaders.USER_AGENT, defaultValue = "unknown") String userAgent,
            final HttpServletRequest request,
            final HttpServletResponse response
    ) {
        rateLimitingService.checkRefreshLimits(HttpRequestUtils.getClientIP(request));

        String refreshToken = authCookieService
                .readRefreshToken(request)
                .orElseThrow(() -> new UnauthorizedException(INVALID_REFRESH_TOKEN_MSG));

        AuthTokens tokens = authService.refresh(
                refreshToken, HttpRequestUtils.getClientIP(request), userAgent
        );
        authCookieService.setCookies(response, tokens);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            final HttpServletRequest request,
            final HttpServletResponse response
    ) {
        String accessToken = authCookieService.readAccessToken(request).orElse(null);
        String refreshToken = authCookieService.readRefreshToken(request).orElse(null);

        authService.logout(accessToken, refreshToken);
        authCookieService.clearCookies(response);

        return ResponseEntity.noContent().build();
    }
}

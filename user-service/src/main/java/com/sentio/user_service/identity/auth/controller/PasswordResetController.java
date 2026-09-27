package com.sentio.user_service.identity.auth.controller;

import com.sentio.shared.web.HttpRequestUtils;
import com.sentio.user_service.identity.auth.dto.request.PasswordResetConfirmRequest;
import com.sentio.user_service.identity.auth.dto.request.PasswordResetRequest;
import com.sentio.user_service.identity.auth.password_reset.PasswordResetService;
import com.sentio.user_service.identity.auth.rate_limiting.RateLimitingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/password-reset")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;
    private final RateLimitingService rateLimitingService;

    @PostMapping("/request")
    public ResponseEntity<Void> requestResetPassword(
            @RequestBody @Valid PasswordResetRequest request,
            final HttpServletRequest httpRequest
    ) {
        rateLimitingService.checkPasswordRequestLimits(request.email(), HttpRequestUtils.getClientIP(httpRequest));

        passwordResetService.requestReset(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/confirm")
    public ResponseEntity<Void> resetPassword(
            @RequestBody @Valid PasswordResetConfirmRequest request,
            final HttpServletRequest httpRequest
    ) {
        rateLimitingService.checkPasswordResetLimits(HttpRequestUtils.getClientIP(httpRequest));

        passwordResetService.resetPassword(request.rawToken(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}

package com.sentio.user_service.identity.auth.controller;

import com.sentio.shared.security.CurrentUserId;
import com.sentio.shared.web.HttpRequestUtils;
import com.sentio.shared.web.ZoneUtility;
import com.sentio.user_service.identity.auth.dto.request.VerifyEmailRequest;
import com.sentio.user_service.identity.auth.rate_limiting.RateLimitingService;
import com.sentio.user_service.identity.auth.verification.EmailVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/email-verification")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;
    private final RateLimitingService rateLimitingService;

    @PostMapping("/confirm")
    public ResponseEntity<Void> verifyEmail(
            @RequestBody @Valid VerifyEmailRequest request,
            final HttpServletRequest httpRequest
    ) {
        rateLimitingService.checkVerifyEmailLimits(HttpRequestUtils.getClientIP(httpRequest));

        emailVerificationService.verifyEmail(request.token());

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resend")
    public ResponseEntity<Void> sendVerificationEmail(
            @CurrentUserId Long userId,
            final HttpServletRequest httpRequest
    ) {
        rateLimitingService.checkSendVerificationLimits(userId, HttpRequestUtils.getClientIP(httpRequest));

        emailVerificationService.sendVerificationEmail(userId, ZoneUtility.getZone(httpRequest));

        return ResponseEntity.accepted().build();
    }
}

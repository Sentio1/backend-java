package com.sentio.user_service.auth.oauth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Component
@Slf4j
public class GoogleOAuth2FailureHandler implements AuthenticationFailureHandler {

    @Value("${app.oauth.failure-redirect-uri}")
    private String redirectUri;

    @Override
    public void onAuthenticationFailure(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull AuthenticationException exception
    ) throws IOException, ServletException {
        log.warn("Google OAuth2 authentication failed: {}", exception.getMessage());
        invalidateSession(request);
        response.sendRedirect(redirectUri + "?error=oauth_failed");
    }

    private void invalidateSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}

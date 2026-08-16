package com.sentio.user_service.auth.oauth;

import com.lisovskyi.security.autoconfigure.cookie.CookieService;
import com.sentio.user_service.auth.AuthService;
import com.sentio.user_service.auth.dto.AuthResult;
import com.sentio.user_service.auth.dto.AuthTokens;
import com.sentio.user_service.auth.oauth.dto.GoogleIdentity;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static com.sentio.user_service.auth.oauth.GoogleOAuth2Constants.ERROR_OAUTH_FAILED;

@Component
@Slf4j
@RequiredArgsConstructor
public class GoogleOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    @Value("${app.oauth.success-redirect-uri}")
    private String successRedirectUri;

    @Value("${app.oauth.failure-redirect-uri}")
    private String failureRedirectUri;

    private final AuthService authService;
    private final CookieService cookieService;

    @Override
    public void onAuthenticationSuccess(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Authentication authentication
    ) throws IOException, ServletException {
        if (!(authentication.getPrincipal() instanceof OAuth2User oAuth2User)) {
            log.warn("Google OAuth2 principal is not an OAuth2User");
            invalidateSession(request);
            response.sendRedirect(failureRedirectUri + ERROR_OAUTH_FAILED);
            return;
        }

        String googleSub = oAuth2User.getAttribute("sub");
        String email = oAuth2User.getAttribute("email");
        String firstName = oAuth2User.getAttribute("given_name");
        String lastName = oAuth2User.getAttribute("family_name");
        Boolean emailVerified = oAuth2User.getAttribute("email_verified");

        if (googleSub == null || email == null) {
            log.warn("Google OAuth2 principal missing required attributes: subPresent={}, emailPresent={}",
                    googleSub != null, email != null);
            invalidateSession(request);
            response.sendRedirect(failureRedirectUri + ERROR_OAUTH_FAILED);
            return;
        }

        GoogleIdentity identity = new GoogleIdentity(
                googleSub, email, firstName, lastName, Boolean.TRUE.equals(emailVerified));

        try {
            AuthResult authResult = authService.loginOrRegisterWithGoogle(identity);
            AuthTokens tokens = authResult.authTokens();

            cookieService.setAccessTokenCookie(response, tokens.accessToken());
            cookieService.setRefreshTokenCookie(response, tokens.refreshToken());

            invalidateSession(request);
            response.sendRedirect(successRedirectUri);
        } catch (Exception e) {
            log.warn("Google OAuth2 login failed after provider authentication", e);
            invalidateSession(request);
            response.sendRedirect(failureRedirectUri + ERROR_OAUTH_FAILED);
        }
    }

    // JwtAuthFilter re-authenticates every request from the JWT cookie alone -
    // the HttpSession that oauth2Login needed mid-handshake serves no purpose
    // afterward. Left alive, it just sits on the server until it times out.
    private void invalidateSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}

package com.sentio.user_service.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lisovskyi.security.autoconfigure.cookie.CookieService;
import com.lisovskyi.web.error.autoconfigure.standard.UnauthorizedException;
import com.sentio.user_service.auth.AuthService;
import com.sentio.user_service.auth.dto.AuthResult;
import com.sentio.user_service.auth.dto.AuthTokens;
import com.sentio.user_service.auth.oauth.dto.GoogleIdentity;
import com.sentio.user_service.user.dto.UserContextResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * No Spring context / real Google redirect here - the handler's own logic (attribute extraction,
 * error handling, cookie issuance, redirect targets, session cleanup) is plain Java once Spring
 * Security hands it an authenticated OAuth2User, so it's tested as such.
 */
@ExtendWith(MockitoExtension.class)
class GoogleOAuth2SuccessHandlerTest {

    private static final String SUCCESS_URI = "http://localhost:5173/dashboard";
    private static final String FAILURE_URI = "http://localhost:5173/login";

    @Mock
    private AuthService authService;

    @Mock
    private CookieService cookieService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Authentication authentication;

    @Mock
    private OAuth2User oAuth2User;

    private GoogleOAuth2SuccessHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GoogleOAuth2SuccessHandler(authService, cookieService);
        ReflectionTestUtils.setField(handler, "successRedirectUri", SUCCESS_URI);
        ReflectionTestUtils.setField(handler, "failureRedirectUri", FAILURE_URI);
        when(authentication.getPrincipal()).thenReturn(oAuth2User);
    }

    private void stubGoogleAttributes(
            String sub, String email, String firstName, String lastName, Boolean emailVerified) {
        when(oAuth2User.getAttribute("sub")).thenReturn(sub);
        when(oAuth2User.getAttribute("email")).thenReturn(email);
        when(oAuth2User.getAttribute("given_name")).thenReturn(firstName);
        when(oAuth2User.getAttribute("family_name")).thenReturn(lastName);
        when(oAuth2User.getAttribute("email_verified")).thenReturn(emailVerified);
    }

    @Test
    void validProfile_issuesCookiesAndRedirectsToSuccessUri() throws Exception {
        stubGoogleAttributes("google-sub-1", "user@sentio.dev", "Jane", "Doe", true);
        UserContextResponse userContext =
                UserContextResponse.builder().id(1L).email("user@sentio.dev").build();
        when(authService.loginOrRegisterWithGoogle(any(GoogleIdentity.class), any(), any()))
                .thenReturn(new AuthResult(new AuthTokens("access-token", "refresh-token"), userContext));
        when(request.getSession(false)).thenReturn(null);

        handler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<GoogleIdentity> identityCaptor = ArgumentCaptor.forClass(GoogleIdentity.class);
        verify(authService).loginOrRegisterWithGoogle(identityCaptor.capture(), any(), any());
        assertThat(identityCaptor.getValue())
                .isEqualTo(new GoogleIdentity("google-sub-1", "user@sentio.dev", "Jane", "Doe", true));

        verify(cookieService).setAccessTokenCookie(response, "access-token");
        verify(cookieService).setRefreshTokenCookie(response, "refresh-token");
        verify(response).sendRedirect(SUCCESS_URI);
    }

    @Test
    void missingEmailVerifiedAttribute_treatedAsNotVerified() throws Exception {
        stubGoogleAttributes("google-sub-1", "user@sentio.dev", "Jane", "Doe", null);
        when(authService.loginOrRegisterWithGoogle(any(GoogleIdentity.class), any(), any()))
                .thenReturn(new AuthResult(
                        new AuthTokens("at", "rt"),
                        UserContextResponse.builder().build()));
        when(request.getSession(false)).thenReturn(null);

        handler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<GoogleIdentity> identityCaptor = ArgumentCaptor.forClass(GoogleIdentity.class);
        verify(authService).loginOrRegisterWithGoogle(identityCaptor.capture(), any(), any());
        assertThat(identityCaptor.getValue().emailVerified()).isFalse();
    }

    @Test
    void missingSubOrEmail_redirectsToFailureWithoutCallingAuthService() throws Exception {
        stubGoogleAttributes(null, "user@sentio.dev", "Jane", "Doe", true);
        when(request.getSession(false)).thenReturn(null);

        handler.onAuthenticationSuccess(request, response, authentication);

        verifyNoInteractions(authService, cookieService);
        verify(response).sendRedirect(FAILURE_URI + "?error=oauth_failed");
    }

    @Test
    void authServiceThrows_redirectsToGenericFailureAndNeverLeaksExceptionMessage() throws Exception {
        stubGoogleAttributes("google-sub-1", "user@sentio.dev", "Jane", "Doe", false);
        when(authService.loginOrRegisterWithGoogle(any(GoogleIdentity.class), any(), any()))
                .thenThrow(new UnauthorizedException("Google account email is not verified"));
        when(request.getSession(false)).thenReturn(null);

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(response).sendRedirect(FAILURE_URI + "?error=oauth_failed");
        verify(response, never()).sendRedirect(eq("Google account email is not verified"));
        verifyNoInteractions(cookieService);
    }

    @Test
    void successfulLogin_invalidatesExistingSession() throws Exception {
        stubGoogleAttributes("google-sub-1", "user@sentio.dev", "Jane", "Doe", true);
        when(authService.loginOrRegisterWithGoogle(any(GoogleIdentity.class), any(), any()))
                .thenReturn(new AuthResult(
                        new AuthTokens("at", "rt"),
                        UserContextResponse.builder().build()));
        HttpSession session = org.mockito.Mockito.mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(session).invalidate();
    }

    @Test
    void noExistingSession_doesNotThrowWhenTryingToInvalidate() throws Exception {
        stubGoogleAttributes("google-sub-1", "user@sentio.dev", "Jane", "Doe", true);
        when(authService.loginOrRegisterWithGoogle(any(GoogleIdentity.class), any(), any()))
                .thenReturn(new AuthResult(
                        new AuthTokens("at", "rt"),
                        UserContextResponse.builder().build()));
        when(request.getSession(false)).thenReturn(null);

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(response).sendRedirect(SUCCESS_URI);
    }
}

package com.sentio.user_service.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleOAuth2FailureHandlerTest {

    private static final String FAILURE_URI = "http://localhost:5173/login";

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    private GoogleOAuth2FailureHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GoogleOAuth2FailureHandler();
        ReflectionTestUtils.setField(handler, "redirectUri", FAILURE_URI);
    }

    @Test
    void anyAuthenticationException_redirectsToGenericFailureUri() throws Exception {
        when(request.getSession(false)).thenReturn(null);
        OAuth2AuthenticationException exception =
                new OAuth2AuthenticationException(new OAuth2Error("access_denied"), "user denied consent on Google's screen");

        handler.onAuthenticationFailure(request, response, exception);

        verify(response).sendRedirect(FAILURE_URI + "?error=oauth_failed");
        // the actual provider-side reason never leaks into the redirect
        verify(response, never()).sendRedirect(eq("user denied consent on Google's screen"));
    }

    @Test
    void existingSession_getsInvalidated() throws Exception {
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);

        handler.onAuthenticationFailure(request, response,
                new OAuth2AuthenticationException(new OAuth2Error("server_error")));

        verify(session).invalidate();
    }

    @Test
    void noSession_doesNotThrow() throws Exception {
        when(request.getSession(false)).thenReturn(null);

        handler.onAuthenticationFailure(request, response,
                new OAuth2AuthenticationException(new OAuth2Error("server_error")));

        verify(response).sendRedirect(FAILURE_URI + "?error=oauth_failed");
    }
}

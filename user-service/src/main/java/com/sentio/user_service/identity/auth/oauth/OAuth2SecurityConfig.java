package com.sentio.user_service.identity.auth.oauth;

import com.lisovskyi.security.autoconfigure.security.SecurityFilterChainCustomizer;
import com.sentio.user_service.identity.auth.oauth.handlers.GoogleOAuth2FailureHandler;
import com.sentio.user_service.identity.auth.oauth.handlers.GoogleOAuth2SuccessHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * Handlers are resolved through {@link ObjectProvider} on purpose, not injected: the starter's
 * DefaultSecurityAutoConfiguration takes every SecurityFilterChainCustomizer in its constructor
 * and also defines the PasswordEncoder bean. Injecting the handlers eagerly closed a cycle -
 * starter config -> this customizer -> GoogleOAuth2SuccessHandler -> AuthService ->
 * PasswordEncoder -> starter config (previously papered over with {@code @Lazy AuthService}).
 * The providers are only dereferenced inside customize(), i.e. while the SecurityFilterChain bean
 * is built, when the starter config instance already exists.
 */
@Configuration
public class OAuth2SecurityConfig {

    @Bean
    public SecurityFilterChainCustomizer securityFilterChainCustomizer(
            ObjectProvider<GoogleOAuth2SuccessHandler> successHandler,
            ObjectProvider<GoogleOAuth2FailureHandler> failureHandler) {
        return http -> http
                // oauth2Login needs a session only for the authorization-request round trip
                // (backed by Spring Session/Redis, so any replica can finish the handshake).
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                // Without this, every unauthenticated hit on a protected endpoint makes
                // ExceptionTranslationFilter save the request into a brand new session -
                // i.e. a Redis write per anonymous 401. There's no login page to return to.
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .oauth2Login(oauth -> oauth
                        .successHandler(successHandler.getObject())
                        .failureHandler(failureHandler.getObject()));
    }
}

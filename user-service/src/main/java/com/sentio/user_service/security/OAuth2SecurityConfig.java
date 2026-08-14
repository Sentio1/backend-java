package com.sentio.user_service.security;

import com.lisovskyi.security.autoconfigure.security.SecurityFilterChainCustomizer;
import com.sentio.user_service.auth.oauth.GoogleOAuth2FailureHandler;
import com.sentio.user_service.auth.oauth.GoogleOAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.http.SessionCreationPolicy;

@Configuration
@RequiredArgsConstructor
public class OAuth2SecurityConfig {

    private final GoogleOAuth2SuccessHandler successHandler;
    private final GoogleOAuth2FailureHandler failureHandler;

    @Bean
    public SecurityFilterChainCustomizer securityFilterChainCustomizer() {
        return http -> http
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .oauth2Login(oauth -> oauth
                    .successHandler(successHandler)
                    .failureHandler(failureHandler)
            );
    }
}

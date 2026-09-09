package com.sentio.shared.config;

import com.sentio.shared.security.CurrentOrganizationIdArgumentResolver;
import com.sentio.shared.security.CurrentUserIdArgumentResolver;
import com.sentio.shared.web.StringToEntityIdConverterFactory;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers {@code @CurrentOrganizationId}/{@code @CurrentUserId} for every service that pulls in
 * shared-core, the same way the security starter's own auto-configuration wires JwtAuthFilter -
 * pull the dependency in, get the behavior, no per-service config needed.
 *
 * <p>{@code @ConditionalOnClass(DispatcherServlet.class)} keeps this inert for any future
 * shared-core consumer that isn't a Spring MVC app.
 */
@Configuration
@ConditionalOnClass(DispatcherServlet.class)
public class SharedWebAutoConfiguration implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(@NonNull List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentOrganizationIdArgumentResolver());
        resolvers.add(new CurrentUserIdArgumentResolver());
    }

    @Override
    public void addFormatters(@NonNull FormatterRegistry registry) {
        registry.addConverterFactory(stringToEntityIdConverterFactory());
    }

    @Bean
    @ConditionalOnMissingBean
    public StringToEntityIdConverterFactory stringToEntityIdConverterFactory() {
        return new StringToEntityIdConverterFactory();
    }
}

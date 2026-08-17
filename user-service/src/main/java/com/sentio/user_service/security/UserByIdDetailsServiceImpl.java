package com.sentio.user_service.security;

import com.lisovskyi.security.autoconfigure.security.SecurityPrincipal;
import com.lisovskyi.security.autoconfigure.security.UserByIdDetailsService;
import com.sentio.user_service.user.entity.User;
import com.sentio.user_service.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/**
 * Bridges the security starter's JwtAuthFilter to this app's own user store. JwtAuthFilter's own
 * {@code jwtAuthFilter(...)} @Bean in the starter is
 * {@code @ConditionalOnBean(UserByIdDetailsService.class)} - without an implementation of this
 * interface present in the context, that bean (and with it, the entire JwtAuthFilter registration
 * in the security filter chain) is silently skipped. No error, no warning: every
 * cookie-authenticated request just falls through as anonymous and gets 401/403 on any endpoint
 * that requires authentication. This class is what makes that filter exist at all.
 */
@Component
@RequiredArgsConstructor
public class UserByIdDetailsServiceImpl implements UserByIdDetailsService {

    private final UserRepository userRepository;

    @Override
    public SecurityPrincipal loadUserById(Long userId) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("No user with id " + userId));

        if (user.getDeletedAt() != null) {
            throw new UsernameNotFoundException("No user with id " + userId);
        }

        return new SecurityUser(user);
    }
}

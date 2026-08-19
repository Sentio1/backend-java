package com.sentio.user_service.security;

import com.lisovskyi.security.autoconfigure.security.SecurityPrincipal;
import com.sentio.user_service.user.entity.User;
import java.util.Collection;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;

@Getter
@RequiredArgsConstructor
/** SecurityUser class. */
public class SecurityUser implements SecurityPrincipal {
    private final transient User user;

    @Override
    public Long getId() {
        return user.getId();
    }

    @Override
    public String getRole() {
        return user.getPlatformRole().toString();
    }

    @Override
    public @NonNull Collection<? extends GrantedAuthority> getAuthorities() {
        return SecurityPrincipal.super.getAuthorities();
    }

    @Override
    public @Nullable String getPassword() {
        return user.getPassword();
    }

    @Override
    public @NonNull String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return SecurityPrincipal.super.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return SecurityPrincipal.super.isAccountNonLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return SecurityPrincipal.super.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return SecurityPrincipal.super.isEnabled();
    }
}

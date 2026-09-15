package com.sentio.user_service.identity.user.api;

import com.lisovskyi.security.autoconfigure.security.SecurityPrincipal;
import java.util.Collection;

import com.sentio.user_service.identity.user.api.enums.PlatformRole;
import com.sentio.user_service.identity.user.api.dto.UserDto;
import lombok.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;

public record SecurityUser(
        long id,
        String email,
        @Nullable String password,
        PlatformRole role
) implements SecurityPrincipal {

    public static SecurityUser from(UserDto user) {
        return new SecurityUser(
                user.id(),
                user.email(),
                user.password(),
                user.platformRole()
        );
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public @NonNull String getUsername() {
        return email;
    }

    @Override
    public @Nullable String getPassword() {
        return password;
    }

    @Override
    public String getRole() {
        return role.name();
    }

    @Override
    public @NonNull Collection<? extends GrantedAuthority> getAuthorities() {
        return SecurityPrincipal.super.getAuthorities();
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

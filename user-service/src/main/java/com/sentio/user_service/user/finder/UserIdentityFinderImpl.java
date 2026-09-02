package com.sentio.user_service.user.finder;

import com.sentio.shared.entity.finder.AbstractEntityFinder;
import com.sentio.user_service.user.entity.UserIdentity;
import com.sentio.user_service.user.enums.AuthProvider;
import com.sentio.user_service.user.repository.UserIdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserIdentityFinderImpl extends AbstractEntityFinder<UserIdentity, Long> implements UserIdentityFinder {

    private final UserIdentityRepository userIdentityRepository;

    @Override
    protected JpaRepository<UserIdentity, Long> getRepository() {
        return userIdentityRepository;
    }

    @Override
    protected String getEntityName() {
        return "UserIdentity";
    }

    @Override
    public Optional<UserIdentity> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId) {
        requireNonNull(provider, providerUserId);
        return userIdentityRepository.findByProviderAndProviderUserId(provider, providerUserId);
    }
}

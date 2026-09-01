package com.sentio.user_service.user.service.finder;

import com.sentio.shared.entity.finder.EntityFinder;
import com.sentio.user_service.user.entity.UserIdentity;
import com.sentio.user_service.user.enums.AuthProvider;

import java.util.Optional;

public interface UserIdentityFinder extends EntityFinder<UserIdentity, Long> {

    Optional<UserIdentity> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}

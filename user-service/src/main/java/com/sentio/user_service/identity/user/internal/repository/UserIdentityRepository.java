package com.sentio.user_service.identity.user.internal.repository;

import com.sentio.user_service.identity.user.api.enums.AuthProvider;
import com.sentio.user_service.identity.user.internal.entity.UserIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {

    Optional<UserIdentity> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}

package com.sentio.user_service.identity.user.internal.repository;

import com.sentio.user_service.identity.user.internal.entity.UserIdentity;
import com.sentio.user_service.identity.user.api.enums.AuthProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {

    Optional<UserIdentity> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}

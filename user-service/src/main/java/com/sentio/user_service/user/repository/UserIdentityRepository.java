package com.sentio.user_service.user.repository;

import com.sentio.user_service.user.entity.UserIdentity;
import com.sentio.user_service.user.enums.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {
    Optional<UserIdentity> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}

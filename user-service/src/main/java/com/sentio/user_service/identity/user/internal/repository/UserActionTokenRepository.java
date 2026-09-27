package com.sentio.user_service.identity.user.internal.repository;

import com.sentio.user_service.identity.user.api.enums.UserActionTokenType;
import com.sentio.user_service.identity.user.internal.model.UserActionToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface UserActionTokenRepository extends JpaRepository<UserActionToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT uat FROM UserActionToken uat " +
            "WHERE uat.hash = :hash " +
            "AND uat.tokenType = :tokenType")
    Optional<UserActionToken> findByHashAndTokenTypeForUpdate(
            @Param("hash") String hash,
            @Param("tokenType") UserActionTokenType tokenType
    );

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE UserActionToken uat
        SET uat.invalidatedAt = :now
        WHERE uat.userId = :userId
        AND uat.tokenType = :tokenType
        AND uat.usedAt IS NULL
        AND uat.invalidatedAt IS NULL
    """)
    int invalidateAllActiveTokens(
            @Param("userId") Long userId,
            @Param("tokenType") UserActionTokenType tokenType,
            @Param("now") Instant now
    );

    @Modifying
    @Query("DELETE FROM UserActionToken uat WHERE uat.expiresAt < :cutoff")
    int deleteAllByExpiresAtBefore(@Param("cutoff") Instant cutoff);
}

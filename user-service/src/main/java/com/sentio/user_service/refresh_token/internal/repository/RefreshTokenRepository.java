package com.sentio.user_service.refresh_token.internal.repository;

import com.sentio.user_service.refresh_token.api.enums.RevokeReason;
import com.sentio.user_service.refresh_token.internal.model.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    Optional<RefreshToken> findByIdAndUserId(long refreshTokenId, long userId);

    List<RefreshToken> findAllByUserIdAndRevokedAtIsNull(long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.userId = :userId AND rt.revokedAt IS NULL ORDER BY rt.createdAt ASC")
    List<RefreshToken> findAllByUserIdAndRevokedAtIsNullOrderByCreatedAtAsc(@Param("userId") long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE RefreshToken rt
            SET rt.revokedAt = :revokedAt, rt.revokeReason = :reason
            WHERE rt.familyId = :familyId AND rt.revokedAt IS NULL
            """)
    int revokeActiveByFamilyId(
            @Param("familyId") UUID familyId,
            @Param("reason") RevokeReason reason,
            @Param("revokedAt") Instant revokedAt);

    int deleteAllByRevokedAtIsNotNullAndRevokedAtBefore(Instant cutoff);

    int deleteAllByExpiresAtBefore(Instant cutoff);
}

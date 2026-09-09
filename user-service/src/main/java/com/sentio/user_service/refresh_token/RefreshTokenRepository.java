package com.sentio.user_service.refresh_token;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByUserIdAndRevokedAtIsNull(long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "SELECT rt FROM RefreshToken rt WHERE rt.user.id = :userId AND rt.revokedAt IS NULL ORDER BY rt.createdAt ASC")
    List<RefreshToken> findAllByUserIdAndRevokedAtIsNullOrderByCreatedAtAsc(@Param("userId") long userId);

    int deleteAllByRevokedAtIsNotNullAndRevokedAtBefore(Instant cutoff);

    int deleteAllByExpiresAtBefore(Instant cutoff);
}

package com.sentio.user_service.identity.user.internal.model;

import com.lisovskyi.jpa.autoconfigure.entity.TimestampedEntity;
import com.sentio.user_service.identity.user.api.enums.UserActionTokenType;
import com.sentio.user_service.identity.user.internal.exception.InvalidUserActionTokenException;
import jakarta.persistence.Column;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;

import static com.sentio.user_service.identity.user.internal.UserActionTokenConstants.HASH_LENGTH;

@Entity
@Table(name = "user_action_tokens")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserActionToken extends TimestampedEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "hash", nullable = false, unique = true, length = HASH_LENGTH)
    private String hash;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "token_type", nullable = false)
    private UserActionTokenType tokenType;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    @Builder
    public UserActionToken(Long userId, String hash, UserActionTokenType tokenType, Duration ttl) {
        this.userId = userId;
        this.hash = hash;
        this.tokenType = tokenType;
        this.expiresAt = Instant.now().plus(ttl);
    }

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isValid() {
        return !isExpired() && !isUsed() && !isInvalidated();
    }

    public boolean isInvalidated() {
        return invalidatedAt != null;
    }

    public void markAsUsed() {
        Instant now = Instant.now();

        if (isUsed()) {
            throw new InvalidUserActionTokenException();
        }
        if (isInvalidated()) {
            throw new InvalidUserActionTokenException();
        }
        if (isExpired(now)) {
            throw new InvalidUserActionTokenException();
        }

        this.usedAt = now;
    }

    public static UserActionToken create(Long userId, String hash, UserActionTokenType tokenType, Duration ttl) {
        return UserActionToken.builder()
                .userId(userId)
                .hash(hash)
                .tokenType(tokenType)
                .ttl(ttl)
                .build();
    }
}

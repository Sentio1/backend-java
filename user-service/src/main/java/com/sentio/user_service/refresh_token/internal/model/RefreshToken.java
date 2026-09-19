package com.sentio.user_service.refresh_token.internal.model;

import com.lisovskyi.jpa.autoconfigure.entity.CreationTimestampedEntity;
import com.sentio.user_service.refresh_token.api.enums.RevokeReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;

import static com.sentio.user_service.refresh_token.internal.RefreshTokenConstants.TOKEN_MAX_LENGTH;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class RefreshToken extends CreationTimestampedEntity {

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, unique = true, length = TOKEN_MAX_LENGTH)
    private String tokenHash;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "ip", columnDefinition = "inet")
    private InetAddress ip;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "family_expires_at", nullable = false, updatable = false)
    private Instant familyExpiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "revoke_reason")
    private RevokeReason revokeReason;

    public void revoke(RevokeReason reason, Instant at) {
        this.revokedAt = at;
        this.revokeReason = reason;
    }
}

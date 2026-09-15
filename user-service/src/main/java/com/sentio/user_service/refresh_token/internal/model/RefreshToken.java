package com.sentio.user_service.refresh_token.internal.model;

import static com.sentio.user_service.refresh_token.internal.RefreshTokenConstants.*;

import com.lisovskyi.jpa.autoconfigure.entity.CreationTimestampedEntity;
import jakarta.persistence.*;
import java.net.InetAddress;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

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

    @Column(name = "token_hash", nullable = false, unique = true, length = TOKEN_MAX_LENGTH)
    private String tokenHash;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "ip", columnDefinition = "inet")
    private InetAddress ip;

    // todo: перевірити чи має бути expiresAt nullable = true
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;
}

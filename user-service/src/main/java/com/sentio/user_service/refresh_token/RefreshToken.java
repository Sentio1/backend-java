package com.sentio.user_service.refresh_token;

import com.lisovskyi.jpa.autoconfigure.entity.CreationTimestampedEntity;
import com.lisovskyi.jpa.autoconfigure.generator.SequenceSize;
import com.sentio.user_service.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.net.InetAddress;
import java.time.Instant;

import static com.sentio.user_service.refresh_token.RefreshTokenConstants.*;

@Entity
@Table(name = "refresh_tokens")
@Getter @Setter
@AllArgsConstructor @NoArgsConstructor
@SuperBuilder
@SequenceSize(size = 50)
public class RefreshToken extends CreationTimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, referencedColumnName = "id")
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = TOKEN_MAX_LENGTH)
    private String tokenHash;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "ip", columnDefinition = "inet")
    private InetAddress ip;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;
}

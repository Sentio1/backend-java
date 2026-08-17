package com.sentio.user_service.organization.entity;

import com.lisovskyi.jpa.autoconfigure.entity.CreationTimestampedEntity;
import com.lisovskyi.jpa.autoconfigure.generator.SequenceSize;
import com.sentio.user_service.organization.enums.OrgRole;
import com.sentio.user_service.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "organization_invites")
@Getter @Setter
@AllArgsConstructor @NoArgsConstructor
@SuperBuilder
@SequenceSize(size = 50)
public class OrganizationInvite extends CreationTimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id", nullable = false, referencedColumnName = "id")
    private Organization organization;

    @Column(name = "email", nullable = false, columnDefinition = "citext")
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private OrgRole role;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by", nullable = false, referencedColumnName = "id")
    private User invitedBy;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;
}

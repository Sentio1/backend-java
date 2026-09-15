package com.sentio.user_service.identity.organization.internal.entity;

import com.lisovskyi.jpa.autoconfigure.entity.CreationTimestampedEntity;
import com.sentio.user_service.identity.organization.api.enums.OrgRole;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "organization_invites")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class OrganizationInvite extends CreationTimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id", nullable = false, referencedColumnName = "id")
    private Organization organization;

    @Column(name = "email", nullable = false, columnDefinition = "citext")
    private String email;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "platformRole", nullable = false)
    private OrgRole role;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "invited_by", nullable = false)
    private Long invitedById;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;
}

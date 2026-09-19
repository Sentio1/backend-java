package com.sentio.user_service.identity.organization.internal.entity;

import com.lisovskyi.jpa.autoconfigure.entity.CreationTimestampedEntity;
import com.sentio.user_service.identity.organization.api.enums.OrgRole;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "organization_members")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class OrganizationMember extends CreationTimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false, referencedColumnName = "id")
    private Organization organization;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "role", nullable = false)
    @Builder.Default
    private OrgRole role = OrgRole.LAWYER;

    @Column(name = "is_default")
    @Builder.Default
    private boolean isDefault = false;

    @Column(name = "joined_at")
    private Instant joinedAt;
}

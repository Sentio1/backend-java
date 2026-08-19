package com.sentio.user_service.organization.entity;

import com.lisovskyi.jpa.autoconfigure.entity.CreationTimestampedEntity;
import com.lisovskyi.jpa.autoconfigure.generator.SequenceSize;
import com.sentio.user_service.organization.enums.OrgRole;
import com.sentio.user_service.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "organization_members")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@SequenceSize(size = 50)
/** OrganizationMember class. */
public class OrganizationMember extends CreationTimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false, referencedColumnName = "id")
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, referencedColumnName = "id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default
    private OrgRole role = OrgRole.LAWYER;

    @Column(name = "is_default")
    @Builder.Default
    private boolean isDefault = false;

    @Column(name = "joined_at")
    private Instant joinedAt;
}

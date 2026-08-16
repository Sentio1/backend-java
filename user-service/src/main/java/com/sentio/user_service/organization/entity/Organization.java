package com.sentio.user_service.organization.entity;

import com.lisovskyi.jpa.autoconfigure.entity.TimestampedEntity;
import com.lisovskyi.jpa.autoconfigure.generator.SequenceSize;
import com.sentio.user_service.organization.enums.PlanTier;
import com.sentio.user_service.organization.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

import static com.sentio.user_service.organization.OrganizationConstants.*;

@Entity
@Table(name = "organizations")
@Getter @Setter
@AllArgsConstructor @NoArgsConstructor
@SuperBuilder
@SequenceSize(size = 50)
public class Organization extends TimestampedEntity {

    @Column(name = "name", nullable = false, length = NAME_LENGTH)
    private String name;

    @Column(name = "slug", unique = true, nullable = false, length = SLUG_LENGTH)
    private String slug;

    @Column(name = "edrpou", length = EDRPOU_LENGTH)
    private String edrpou;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default
    private PlanTier plan = PlanTier.SOLO;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.TRIALING;

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}

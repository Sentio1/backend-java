package com.sentio.user_service.identity.organization.internal.entity;

import com.lisovskyi.jpa.autoconfigure.entity.TimestampedEntity;
import com.sentio.user_service.identity.organization.api.enums.PlanTier;
import com.sentio.user_service.identity.organization.api.enums.SubscriptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

import static com.sentio.user_service.identity.organization.internal.OrganizationConstants.*;

@Entity
@Table(name = "organizations")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@SQLRestriction("deleted_at IS NULL")
public class Organization extends TimestampedEntity {

    @Column(name = "name", nullable = false, length = NAME_LENGTH)
    private String name;

    @Column(name = "slug", unique = true, nullable = false, length = SLUG_LENGTH)
    private String slug;

    @Column(name = "edrpou", length = EDRPOU_LENGTH)
    private String edrpou;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "plan", nullable = false)
    @Builder.Default
    private PlanTier plan = PlanTier.SOLO;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "subscription_status", nullable = false)
    @Builder.Default
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.TRIALING;

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}

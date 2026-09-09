package com.sentio.user_service.organization.dto.organization;

import com.sentio.user_service.organization.enums.PlanTier;
import com.sentio.user_service.organization.enums.SubscriptionStatus;

/** OrganizationResponse record. */
public record OrganizationResponse(
        Long id, String name, String slug, String edrpou, PlanTier plan, SubscriptionStatus subscriptionStatus) {}

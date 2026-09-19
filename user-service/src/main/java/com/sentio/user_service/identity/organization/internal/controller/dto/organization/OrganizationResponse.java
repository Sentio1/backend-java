package com.sentio.user_service.identity.organization.internal.controller.dto.organization;

import com.sentio.user_service.identity.organization.api.enums.PlanTier;
import com.sentio.user_service.identity.organization.api.enums.SubscriptionStatus;

public record OrganizationResponse(
        Long id, String name, String slug, String edrpou, PlanTier plan, SubscriptionStatus subscriptionStatus) {}

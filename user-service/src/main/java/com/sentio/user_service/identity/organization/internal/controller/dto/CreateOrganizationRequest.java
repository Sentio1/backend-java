package com.sentio.user_service.identity.organization.internal.controller.dto;

import com.sentio.user_service.identity.organization.api.enums.PlanTier;

/** CreateOrganizationRequest record. */
public record CreateOrganizationRequest(String orgName, String edrpou, PlanTier plan) {}

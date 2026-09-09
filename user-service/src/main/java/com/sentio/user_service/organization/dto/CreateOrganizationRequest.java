package com.sentio.user_service.organization.dto;

import com.sentio.user_service.organization.enums.PlanTier;

/** CreateOrganizationRequest record. */
public record CreateOrganizationRequest(String orgName, String edrpou, PlanTier plan) {}

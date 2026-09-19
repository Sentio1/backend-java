package com.sentio.user_service.identity.organization.api.dto;

import com.sentio.user_service.identity.organization.api.enums.PlanTier;
import jakarta.validation.constraints.NotBlank;

public record CreateOrganizationRequest(@NotBlank String orgName, String edrpou, PlanTier plan) {}

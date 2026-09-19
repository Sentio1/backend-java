package com.sentio.user_service.identity.organization.internal.service;

import com.lisovskyi.web.error.autoconfigure.standard.BadRequestException;
import com.sentio.shared.entity.id.user.UserId;
import com.sentio.user_service.identity.organization.api.dto.OrganizationMemberDto;
import com.sentio.user_service.identity.organization.api.enums.OrgRole;
import com.sentio.user_service.identity.organization.api.enums.PlanTier;
import com.sentio.user_service.identity.organization.api.enums.SubscriptionStatus;
import com.sentio.user_service.identity.organization.internal.entity.Organization;
import com.sentio.user_service.identity.organization.internal.entity.OrganizationMember;
import com.sentio.user_service.identity.organization.internal.mapper.OrganizationMemberMapper;
import com.sentio.user_service.identity.organization.internal.repository.OrganizationMemberRepository;
import com.sentio.user_service.identity.organization.internal.repository.OrganizationRepository;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrganizationCreationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final OrganizationMemberMapper organizationMemberMapper;

    @Timed(value = "user-service.organization.create", description = "Time spent creating an organization")
    public OrganizationMemberDto createOwnerMembership(UserId userId, String orgName, String edrpou, PlanTier plan) {
        log.debug("Creating owner membership for user: {} with orgName: {}", userId.id(), orgName);
        if (orgName == null || orgName.isBlank()) {
            log.warn("Failed to create owner membership: missing organization name");
            throw new BadRequestException("Organization name is required to create a new organization", "ORGANIZATION_NAME_REQUIRED");
        }

        Organization organization = Organization.builder()
                .name(orgName)
                .slug(UUID.randomUUID().toString())
                .edrpou(edrpou)
                .plan(plan != null ? plan : PlanTier.SOLO)
                .subscriptionStatus(SubscriptionStatus.TRIALING)
                .trialEndsAt(Instant.now().plus(14, ChronoUnit.DAYS))
                .build();
        organizationRepository.save(organization);
        log.info("Created new organization: {} with plan: {}", organization.getId(), organization.getPlan());

        OrganizationMember membership = OrganizationMember.builder()
                .userId(userId.id())
                .organization(organization)
                .role(OrgRole.OWNER)
                .isDefault(true)
                .build();

        OrganizationMember savedMembership = organizationMemberRepository.save(membership);
        log.info("Successfully added user: {} as OWNER to organization: {}", userId, organization.getId());

        return organizationMemberMapper.toDto(savedMembership);
    }
}

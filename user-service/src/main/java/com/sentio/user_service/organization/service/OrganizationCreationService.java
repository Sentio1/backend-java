package com.sentio.user_service.organization.service;

import com.sentio.user_service.organization.entity.Organization;
import com.sentio.user_service.organization.entity.OrganizationMember;
import com.sentio.user_service.organization.enums.OrgRole;
import com.sentio.user_service.organization.enums.PlanTier;
import com.sentio.user_service.organization.enums.SubscriptionStatus;
import com.sentio.user_service.organization.repository.OrganizationMemberRepository;
import com.sentio.user_service.organization.repository.OrganizationRepository;
import com.sentio.user_service.user.entity.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrganizationCreationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;

    @Timed(value = "user-service.organization.create", description = "Time spent creating an organization")
    public OrganizationMember createOwnerMembership(User user, String orgName, String edrpou, PlanTier plan) {
        log.debug("Creating owner membership for user: {} with orgName: {}", user.getId(), orgName);
        if (orgName == null || orgName.isBlank()) {
            log.warn("Failed to create owner membership: missing organization name");
            throw new IllegalArgumentException("Organization name is required to create a new organization");
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
                .user(user)
                .organization(organization)
                .role(OrgRole.OWNER)
                .isDefault(true)
                .build();

        OrganizationMember savedMembership = organizationMemberRepository.save(membership);
        log.info("Successfully added user: {} as OWNER to organization: {}", user.getId(), organization.getId());

        return savedMembership;
    }
}

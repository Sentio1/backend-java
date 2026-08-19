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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
/** OrganizationCreationService class. */
public class OrganizationCreationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;

    public OrganizationMember createOwnerMembership(User user, String orgName, String edrpou, PlanTier plan) {
        if (orgName == null || orgName.isBlank()) {
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

        OrganizationMember membership = OrganizationMember.builder()
                .user(user)
                .organization(organization)
                .role(OrgRole.OWNER)
                .isDefault(true)
                .build();

        return organizationMemberRepository.save(membership);
    }
}

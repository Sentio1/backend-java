package com.sentio.user_service.organization;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.user_service.organization.entity.Organization;
import com.sentio.user_service.organization.entity.OrganizationMember;
import com.sentio.user_service.organization.enums.OrgRole;
import com.sentio.user_service.organization.enums.PlanTier;
import com.sentio.user_service.organization.enums.SubscriptionStatus;
import com.sentio.user_service.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Everything about creating an organization and attaching a user to one as a
 * member - used by local registration (OWNER creates a new org, LAWYER/ASSISTANT
 * join an existing one by slug) and by Google sign-up (always OWNER of a brand
 * new org, since there's no invite flow through OAuth).
 */
@Component
@RequiredArgsConstructor
public class OrganizationProvisioningService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;

    public OrganizationMember createOwnerMembership(User user, String orgName, String edrpou, PlanTier plan) {
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

    public OrganizationMember joinExistingOrganization(User user, String slug, OrgRole role) {
        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("Organization slug is required to join an existing organization");
        }

        Organization organization = organizationRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "slug", slug));

        OrganizationMember membership = OrganizationMember.builder()
                .user(user)
                .organization(organization)
                .role(role)
                .isDefault(true)
                .build();

        return organizationMemberRepository.save(membership);
    }
}

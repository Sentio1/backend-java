package com.sentio.user_service.organization;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.user_service.organization.entity.Organization;
import com.sentio.user_service.organization.entity.OrganizationMember;
import com.sentio.user_service.organization.enums.OrgRole;
import com.sentio.user_service.organization.enums.PlanTier;
import com.sentio.user_service.organization.enums.SubscriptionStatus;
import com.sentio.user_service.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationProvisioningServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private OrganizationMemberRepository organizationMemberRepository;

    @InjectMocks
    private OrganizationProvisioningService organizationProvisioning;

    private User user;

    private User newUser() {
        User u = User.builder().email("owner@sentio.dev").build();
        u.setId(1L);
        return u;
    }

    @Test
    void createOwnerMembership_createsTrialingOrgWithDefaultPlanAndOwnerMembership() {
        user = newUser();
        when(organizationRepository.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));
        when(organizationMemberRepository.save(any(OrganizationMember.class))).thenAnswer(inv -> inv.getArgument(0));

        OrganizationMember membership = organizationProvisioning.createOwnerMembership(user, "Test Firm", "1234567", null);

        assertThat(membership.getRole()).isEqualTo(OrgRole.OWNER);
        assertThat(membership.isDefault()).isTrue();
        assertThat(membership.getUser()).isEqualTo(user);

        ArgumentCaptor<Organization> orgCaptor = ArgumentCaptor.forClass(Organization.class);
        verify(organizationRepository).save(orgCaptor.capture());
        Organization org = orgCaptor.getValue();
        assertThat(org.getName()).isEqualTo("Test Firm");
        assertThat(org.getEdrpou()).isEqualTo("1234567");
        assertThat(org.getPlan()).isEqualTo(PlanTier.SOLO); // null plan defaults to SOLO
        assertThat(org.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.TRIALING);
        assertThat(org.getTrialEndsAt()).isAfter(Instant.now().plusSeconds(13 * 24 * 3600));
        assertThat(org.getSlug()).isNotBlank();
    }

    @Test
    void createOwnerMembership_respectsExplicitPlan() {
        user = newUser();
        when(organizationRepository.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));
        when(organizationMemberRepository.save(any(OrganizationMember.class))).thenAnswer(inv -> inv.getArgument(0));

        organizationProvisioning.createOwnerMembership(user, "Test Firm", null, PlanTier.FIRM);

        ArgumentCaptor<Organization> orgCaptor = ArgumentCaptor.forClass(Organization.class);
        verify(organizationRepository).save(orgCaptor.capture());
        assertThat(orgCaptor.getValue().getPlan()).isEqualTo(PlanTier.FIRM);
    }

    @Test
    void joinExistingOrganization_blankSlug_throwsIllegalArgumentException() {
        user = newUser();

        assertThatThrownBy(() -> organizationProvisioning.joinExistingOrganization(user, " ", OrgRole.LAWYER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Organization slug is required");
    }

    @Test
    void joinExistingOrganization_unknownSlug_throwsResourceNotFoundException() {
        user = newUser();
        when(organizationRepository.findBySlug("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> organizationProvisioning.joinExistingOrganization(user, "unknown", OrgRole.LAWYER))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void joinExistingOrganization_knownSlug_createsMembershipWithRequestedRole() {
        user = newUser();
        Organization existing = Organization.builder().name("Acme Legal").slug("acme").build();
        existing.setId(10L);
        when(organizationRepository.findBySlug("acme")).thenReturn(Optional.of(existing));
        when(organizationMemberRepository.save(any(OrganizationMember.class))).thenAnswer(inv -> inv.getArgument(0));

        OrganizationMember membership = organizationProvisioning.joinExistingOrganization(user, "acme", OrgRole.ASSISTANT);

        assertThat(membership.getOrganization()).isEqualTo(existing);
        assertThat(membership.getRole()).isEqualTo(OrgRole.ASSISTANT);
        assertThat(membership.isDefault()).isTrue();
    }
}

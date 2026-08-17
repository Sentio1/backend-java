package com.sentio.user_service.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sentio.user_service.organization.entity.Organization;
import com.sentio.user_service.organization.entity.OrganizationMember;
import com.sentio.user_service.organization.enums.OrgRole;
import com.sentio.user_service.organization.enums.PlanTier;
import com.sentio.user_service.organization.enums.SubscriptionStatus;
import com.sentio.user_service.organization.repository.OrganizationMemberRepository;
import com.sentio.user_service.organization.repository.OrganizationRepository;
import com.sentio.user_service.organization.service.OrganizationCreationService;
import com.sentio.user_service.user.entity.User;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
/** OrganizationCreationServiceTest class. */
class OrganizationCreationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationMemberRepository organizationMemberRepository;

    @InjectMocks
    private OrganizationCreationService organizationProvisioning;

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

        OrganizationMember membership =
                organizationProvisioning.createOwnerMembership(user, "Test Firm", "1234567", null);

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
    void createOwnerMembership_blankOrgName_throwsIllegalArgumentException() {
        user = newUser();

        assertThatThrownBy(() -> organizationProvisioning.createOwnerMembership(user, "  ", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Organization name is required");

        verify(organizationRepository, never()).save(any());
    }

    // joinExistingOrganization is gone - non-OWNER membership only ever comes through
    // OrganizationInviteService's accept flow now (see OrganizationInviteServiceTest).
}

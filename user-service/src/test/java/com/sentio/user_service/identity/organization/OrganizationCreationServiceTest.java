package com.sentio.user_service.identity.organization;

import com.lisovskyi.web.error.autoconfigure.standard.BadRequestException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sentio.shared.entity.id.user.UserId;
import com.sentio.user_service.identity.organization.api.dto.OrganizationMemberDto;
import com.sentio.user_service.identity.organization.internal.entity.Organization;
import com.sentio.user_service.identity.organization.internal.entity.OrganizationMember;
import com.sentio.user_service.identity.organization.api.enums.OrgRole;
import com.sentio.user_service.identity.organization.api.enums.PlanTier;
import com.sentio.user_service.identity.organization.api.enums.SubscriptionStatus;
import com.sentio.user_service.identity.organization.internal.mapper.OrganizationMemberMapper;
import com.sentio.user_service.identity.organization.internal.repository.OrganizationMemberRepository;
import com.sentio.user_service.identity.organization.internal.repository.OrganizationRepository;
import com.sentio.user_service.identity.organization.internal.service.OrganizationCreationService;
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

    @Mock
    private OrganizationMemberMapper organizationMemberMapper;

    @InjectMocks
    private OrganizationCreationService organizationProvisioning;

    private UserId userId() {
        return UserId.of(1L);
    }

    @Test
    void createOwnerMembership_createsTrialingOrgWithDefaultPlanAndOwnerMembership() {
        when(organizationRepository.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));
        when(organizationMemberRepository.save(any(OrganizationMember.class))).thenAnswer(inv -> inv.getArgument(0));
        OrganizationMemberDto expected = new OrganizationMemberDto(1L, 1L, "Test Firm", OrgRole.OWNER);
        when(organizationMemberMapper.toDto(any(OrganizationMember.class))).thenReturn(expected);

        OrganizationMemberDto membership =
                organizationProvisioning.createOwnerMembership(userId(), "Test Firm", "1234567", null);

        assertThat(membership).isEqualTo(expected);

        ArgumentCaptor<OrganizationMember> memberCaptor = ArgumentCaptor.forClass(OrganizationMember.class);
        verify(organizationMemberRepository).save(memberCaptor.capture());
        OrganizationMember savedMember = memberCaptor.getValue();
        assertThat(savedMember.getRole()).isEqualTo(OrgRole.OWNER);
        assertThat(savedMember.isDefault()).isTrue();
        assertThat(savedMember.getUserId()).isEqualTo(1L);

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
        when(organizationRepository.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));
        when(organizationMemberRepository.save(any(OrganizationMember.class))).thenAnswer(inv -> inv.getArgument(0));
        when(organizationMemberMapper.toDto(any(OrganizationMember.class)))
                .thenReturn(new OrganizationMemberDto(1L, 1L, "Test Firm", OrgRole.OWNER));

        organizationProvisioning.createOwnerMembership(userId(), "Test Firm", null, PlanTier.FIRM);

        ArgumentCaptor<Organization> orgCaptor = ArgumentCaptor.forClass(Organization.class);
        verify(organizationRepository).save(orgCaptor.capture());
        assertThat(orgCaptor.getValue().getPlan()).isEqualTo(PlanTier.FIRM);
    }

    @Test
    void createOwnerMembership_blankOrgName_throwsBadRequest() {
        assertThatThrownBy(() -> organizationProvisioning.createOwnerMembership(userId(), "  ", null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Organization name is required");

        verify(organizationRepository, never()).save(any());
    }

    // joinExistingOrganization is gone - non-OWNER membership only ever comes through
    // OrganizationInviteService's accept flow now (see OrganizationInviteServiceTest).
}

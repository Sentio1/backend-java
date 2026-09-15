package com.sentio.user_service.identity.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.user_service.identity.organization.api.dto.OrganizationMemberDto;
import com.sentio.user_service.identity.organization.api.dto.OrganizationMemberResponse;
import com.sentio.user_service.identity.organization.api.enums.OrgRole;
import com.sentio.user_service.identity.organization.internal.controller.dto.organization.OrganizationResponse;
import com.sentio.user_service.identity.organization.internal.controller.dto.organization.UpdateOrganizationRequest;
import com.sentio.user_service.identity.organization.internal.entity.Organization;
import com.sentio.user_service.identity.organization.internal.entity.OrganizationMember;
import com.sentio.user_service.identity.organization.internal.mapper.OrganizationMapper;
import com.sentio.user_service.identity.organization.internal.mapper.OrganizationMemberMapper;
import com.sentio.user_service.identity.organization.internal.repository.OrganizationMemberRepository;
import com.sentio.user_service.identity.organization.internal.repository.OrganizationRepository;
import com.sentio.user_service.identity.organization.internal.service.OrganizationServiceImpl;
import com.sentio.user_service.identity.user.api.dto.UserContextResponse;
import com.sentio.user_service.identity.user.api.service.UserService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
/** OrganizationServiceTest class. */
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationMemberRepository organizationMemberRepository;

    @Mock
    private OrganizationMapper organizationMapper;

    @Mock
    private OrganizationMemberMapper organizationMemberMapper;

    @Mock
    private UserService userService;

    @InjectMocks
    private OrganizationServiceImpl organizationService;

    private Organization org(long orgId) {
        Organization org =
                Organization.builder().name("Acme Legal").slug("acme").build();
        org.setId(orgId);
        return org;
    }

    private OrganizationMember member(long orgId, long userId, OrgRole role) {
        return OrganizationMember.builder()
                .organization(org(orgId))
                .userId(userId)
                .role(role)
                .build();
    }

    // OrganizationMember only carries a userId, so toMemberResponse (the thing under
    // test here) delegates building the response's "user" part to UserService - stub
    // that hop and hand back the OrganizationMemberResponse it should produce.
    private OrganizationMemberResponse stubMemberResponse(OrganizationMember member, UserContextResponse userContext) {
        OrganizationMemberDto dto = new OrganizationMemberDto(
                1L, member.getOrganization().getId(), member.getOrganization().getName(), member.getRole());
        when(organizationMemberMapper.toDto(member)).thenReturn(dto);
        when(userService.buildUserContext(member.getUserId(), dto)).thenReturn(userContext);
        return new OrganizationMemberResponse(dto.organizationId(), dto.orgRole(), member.isDefault(), userContext);
    }

    @Test
    void existingOrg_updatesNameAndReturnsMappedResponse() {
        Organization organization =
                Organization.builder().name("Old Name").slug("acme").build();
        organization.setId(10L);
        when(organizationRepository.findByIdLocked(10L)).thenReturn(Optional.of(organization));
        when(organizationRepository.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));

        OrganizationResponse expected = new OrganizationResponse(10L, "New Name", "acme", null, null, null);
        when(organizationMapper.toResponse(any(Organization.class))).thenReturn(expected);

        OrganizationResponse result =
                organizationService.updateOrganization(10L, new UpdateOrganizationRequest("New Name"));

        assertThat(result).isEqualTo(expected);

        ArgumentCaptor<Organization> captor = ArgumentCaptor.forClass(Organization.class);
        verify(organizationRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("New Name");
    }

    @Test
    void unknownId_throwsResourceNotFoundExceptionAndNeverSaves() {
        when(organizationRepository.findByIdLocked(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () -> organizationService.updateOrganization(999L, new UpdateOrganizationRequest("New Name")))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(organizationRepository, never()).save(any());
    }

    // ---- deleteOrganizationMember ----------------------------------------

    @Nested
    /** DeleteOrganizationMember class. */
    class DeleteOrganizationMember {

        @Test
        void nonOwner_deletesWithoutCheckingOwnerCount() {
            when(organizationRepository.existsById(1L)).thenReturn(true);
            OrganizationMember lawyer = member(1L, 2L, OrgRole.LAWYER);
            when(organizationMemberRepository.findByUserIdAndOrganizationId(2L, 1L))
                    .thenReturn(Optional.of(lawyer));

            organizationService.deleteOrganizationMember(1L, 2L);

            verify(organizationMemberRepository).delete(lawyer);
            // A non-OWNER's removal can never orphan the organization, so the
            // owner-count query has no reason to run at all.
            verify(organizationMemberRepository, never()).countByOrganizationIdAndRole(anyLong(), any());
        }

        @Test
        void ownerWithCoOwners_deletesSuccessfully() {
            when(organizationRepository.existsById(1L)).thenReturn(true);
            OrganizationMember owner = member(1L, 2L, OrgRole.OWNER);
            when(organizationMemberRepository.findByUserIdAndOrganizationId(2L, 1L))
                    .thenReturn(Optional.of(owner));
            when(organizationMemberRepository.countByOrganizationIdAndRole(1L, OrgRole.OWNER))
                    .thenReturn(2L);

            organizationService.deleteOrganizationMember(1L, 2L);

            verify(organizationMemberRepository).delete(owner);
        }

        @Test
        void lastOwner_throwsIllegalArgumentExceptionAndNeverDeletes() {
            when(organizationRepository.existsById(1L)).thenReturn(true);
            OrganizationMember lastOwner = member(1L, 2L, OrgRole.OWNER);
            when(organizationMemberRepository.findByUserIdAndOrganizationId(2L, 1L))
                    .thenReturn(Optional.of(lastOwner));
            when(organizationMemberRepository.countByOrganizationIdAndRole(1L, OrgRole.OWNER))
                    .thenReturn(1L);

            assertThatThrownBy(() -> organizationService.deleteOrganizationMember(1L, 2L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("last owner");

            verify(organizationMemberRepository, never()).delete(any());
        }

        @Test
        void notAMember_throwsResourceNotFoundExceptionAndNeverDeletes() {
            when(organizationRepository.existsById(1L)).thenReturn(true);
            when(organizationMemberRepository.findByUserIdAndOrganizationId(2L, 1L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> organizationService.deleteOrganizationMember(1L, 2L))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(organizationMemberRepository, never()).delete(any());
        }

        @Test
        void unknownOrg_throwsResourceNotFoundExceptionAndNeverChecksMembership() {
            when(organizationRepository.existsById(1L)).thenReturn(false);

            assertThatThrownBy(() -> organizationService.deleteOrganizationMember(1L, 2L))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(organizationMemberRepository, never()).findByUserIdAndOrganizationId(anyLong(), anyLong());
        }
    }

    // ---- patchRoleForMember -----------------------------------------------

    @Nested
    /** PatchRoleForMember class. */
    class PatchRoleForMember {

        @Test
        void existingMember_updatesRoleAndReturnsMappedResponse() {
            when(organizationRepository.existsById(1L)).thenReturn(true);
            OrganizationMember member = member(1L, 2L, OrgRole.LAWYER);
            when(organizationMemberRepository.findByUserIdAndOrganizationId(2L, 1L))
                    .thenReturn(Optional.of(member));
            when(organizationMemberRepository.save(any(OrganizationMember.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            UserContextResponse userContext =
                    UserContextResponse.builder().orgName("Acme Legal").orgRole("ASSISTANT").build();
            OrganizationMemberResponse expected = stubMemberResponse(member, userContext);

            OrganizationMemberResponse result = organizationService.patchRoleForMember(1L, 2L, OrgRole.ASSISTANT);

            assertThat(result).isEqualTo(expected);
            assertThat(member.getRole()).isEqualTo(OrgRole.ASSISTANT);
            verify(organizationMemberRepository).save(member);
        }

        @Test
        void notAMember_throwsResourceNotFoundException() {
            when(organizationRepository.existsById(1L)).thenReturn(true);
            when(organizationMemberRepository.findByUserIdAndOrganizationId(2L, 1L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> organizationService.patchRoleForMember(1L, 2L, OrgRole.ASSISTANT))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(organizationMemberRepository, never()).save(any());
        }

        @Test
        void unknownOrg_throwsResourceNotFoundExceptionAndNeverChecksMembership() {
            when(organizationRepository.existsById(1L)).thenReturn(false);

            assertThatThrownBy(() -> organizationService.patchRoleForMember(1L, 2L, OrgRole.ASSISTANT))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(organizationMemberRepository, never()).findByUserIdAndOrganizationId(anyLong(), anyLong());
        }
    }

    // ---- getAllOrganizationMembers -----------------------------------------

    @Nested
    /** GetAllOrganizationMembers class. */
    class GetAllOrganizationMembers {

        @Test
        void delegatesToRepositoryAndMapsEachMember() {
            OrganizationMember member = member(1L, 2L, OrgRole.LAWYER);
            Pageable pageable = PageRequest.of(0, 20);
            when(organizationMemberRepository.findAllByOrganizationIdAndUserDeletedAtIsNull(1L, pageable))
                    .thenReturn(new PageImpl<>(List.of(member), pageable, 1));
            UserContextResponse userContext =
                    UserContextResponse.builder().orgName("Acme Legal").orgRole("LAWYER").build();
            OrganizationMemberResponse mapped = stubMemberResponse(member, userContext);

            var result = organizationService.getAllOrganizationMembers(1L, pageable);

            assertThat(result.content()).containsExactly(mapped);
            assertThat(result.totalElements()).isEqualTo(1);
        }
    }
}

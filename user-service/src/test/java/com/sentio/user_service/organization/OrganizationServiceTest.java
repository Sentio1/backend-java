package com.sentio.user_service.organization;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.user_service.organization.dto.OrganizationResponse;
import com.sentio.user_service.organization.dto.UpdateOrganizationRequest;
import com.sentio.user_service.organization.entity.Organization;
import com.sentio.user_service.organization.mapper.OrganizationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private OrganizationMapper organizationMapper;

    @InjectMocks
    private OrganizationService organizationService;

    @Test
    void existingOrg_updatesNameAndReturnsMappedResponse() {
        Organization organization = Organization.builder().name("Old Name").slug("acme").build();
        organization.setId(10L);
        when(organizationRepository.findById(10L)).thenReturn(Optional.of(organization));
        when(organizationRepository.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));

        OrganizationResponse expected = new OrganizationResponse(10L, "New Name", "acme", null, null, null);
        when(organizationMapper.toResponse(any(Organization.class))).thenReturn(expected);

        OrganizationResponse result = organizationService.updateOrganization(10L, new UpdateOrganizationRequest("New Name"));

        assertThat(result).isEqualTo(expected);

        ArgumentCaptor<Organization> captor = ArgumentCaptor.forClass(Organization.class);
        verify(organizationRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("New Name");
    }

    @Test
    void unknownId_throwsResourceNotFoundExceptionAndNeverSaves() {
        when(organizationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> organizationService.updateOrganization(999L, new UpdateOrganizationRequest("New Name")))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(organizationRepository, never()).save(any());
    }
}

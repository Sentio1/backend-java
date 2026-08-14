package com.sentio.user_service.organization;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.user_service.organization.dto.OrganizationResponse;
import com.sentio.user_service.organization.dto.UpdateOrganizationRequest;
import com.sentio.user_service.organization.entity.Organization;
import com.sentio.user_service.organization.mapper.OrganizationMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMapper organizationMapper;

    @Transactional
    public OrganizationResponse updateOrganization(long id, UpdateOrganizationRequest updateRequest) {
        // Authorization (is this user allowed to manage org #id) is already
        // enforced by @PreAuthorize on the controller via OrganizationSecurity -
        // this method only has to do the update.
        Organization organization = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "id", id));

        organization.setName(updateRequest.name());

        return organizationMapper.toResponse(organizationRepository.save(organization));
    }
}

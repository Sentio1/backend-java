package com.sentio.user_service.organization;

import com.sentio.user_service.organization.dto.OrganizationResponse;
import com.sentio.user_service.organization.dto.UpdateOrganizationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @PutMapping("/{id}")
    @PreAuthorize("@organizationSecurity.canManage(#id, authentication)")
    public ResponseEntity<OrganizationResponse> updateOrganization(
            @PathVariable long id,
            @RequestBody @Valid UpdateOrganizationRequest updateRequest
    ) {
        OrganizationResponse organizationResponse = organizationService.updateOrganization(id, updateRequest);
        return ResponseEntity.ok(organizationResponse);
    }
}

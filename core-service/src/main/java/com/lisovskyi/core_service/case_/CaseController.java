package com.lisovskyi.core_service.case_;

import static com.lisovskyi.core_service.entity.CoreEntityConstants.DELETE_REASON_LENGTH;

import com.lisovskyi.core_service.case_.dto.request.CaseCreateRequest;
import com.lisovskyi.core_service.case_.dto.request.CaseUpdateRequest;
import com.lisovskyi.core_service.case_.dto.response.CaseResponse;
import com.lisovskyi.core_service.case_.service.CaseService;
import com.sentio.shared.dto.PageResponse;
import com.sentio.shared.entity.id.case_.CaseId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import com.sentio.shared.entity.id.user.UserId;
import com.sentio.shared.security.CurrentOrganizationId;
import com.sentio.shared.security.CurrentUserId;
import com.sentio.shared.web.LocationUtility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cases")
@RequiredArgsConstructor
@Validated
public class CaseController {

    private final CaseService caseService;

    @GetMapping("/{caseId}")
    public ResponseEntity<CaseResponse> getCaseById(
            @PathVariable CaseId caseId,
            @CurrentOrganizationId OrganizationId organizationId
    ) {
        return ResponseEntity.ok(caseService.getCaseByIdAndOrganizationId(caseId, organizationId));
    }

    @GetMapping
    public ResponseEntity<PageResponse<CaseResponse>> getAllCases(
            @CurrentOrganizationId OrganizationId organizationId,
            final Pageable pageable
    ) {
       return ResponseEntity.ok(caseService.getAllCasesByOrganizationId(organizationId, pageable));
    }

    @PostMapping
    public ResponseEntity<CaseResponse> createCase(
            @CurrentOrganizationId OrganizationId organizationId,
            @CurrentUserId UserId createdById,
            @RequestBody @Valid CaseCreateRequest request
    ) {
        CaseResponse caseResponse = caseService.createCase(organizationId, createdById, request);
        return LocationUtility.createdWithLocation(caseResponse.id(), caseResponse);
    }

    @PatchMapping("/{caseId}")
    public ResponseEntity<CaseResponse> updateCase(
            @PathVariable CaseId caseId,
            @CurrentOrganizationId OrganizationId organizationId,
            @RequestBody @Valid CaseUpdateRequest request
    ) {
        return ResponseEntity.ok(caseService.updateCase(caseId, organizationId, request));
    }

    @DeleteMapping("/{caseId}")
    public ResponseEntity<Void> deleteCase(
            @PathVariable CaseId caseId,
            @CurrentOrganizationId OrganizationId organizationId,
            @CurrentUserId UserId deletedById,
            @RequestParam(required = false) @Size(max = DELETE_REASON_LENGTH) String deleteReason
    ) {
        caseService.deleteCase(caseId, organizationId, deletedById, deleteReason);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{caseId}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> restoreCase(
            @PathVariable CaseId caseId,
            @CurrentOrganizationId OrganizationId organizationId,
            @CurrentUserId UserId restoredById
    ) {
        caseService.restoreCase(caseId, organizationId, restoredById);
        return ResponseEntity.noContent().build();
    }
}

package com.sentio.core_service.litigation.internal.controller;

import com.sentio.core_service.litigation.internal.service.CasePartyService;

import static com.sentio.core_service.common.model.CoreEntityConstants.DELETE_REASON_LENGTH;

import com.sentio.core_service.litigation.internal.controller.dto.CasePartyCreateRequest;
import com.sentio.core_service.litigation.internal.controller.dto.CasePartyUpdateRequest;
import com.sentio.core_service.litigation.internal.controller.dto.CasePartyResponse;
import com.sentio.shared.dto.PageResponse;
import com.sentio.shared.entity.id.case_.CaseId;
import com.sentio.shared.entity.id.case_party.CasePartyId;
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
@RequestMapping("/cases/{caseId}/case-parties")
@RequiredArgsConstructor
@Validated
public class CasePartyController {

    private final CasePartyService casePartyService;

    @GetMapping("/{casePartyId}")
    public ResponseEntity<CasePartyResponse> getCaseParty(
            @PathVariable CaseId caseId,
            @PathVariable CasePartyId casePartyId,
            @CurrentOrganizationId OrganizationId organizationId
    ) {
        return ResponseEntity.ok(casePartyService.getCaseParty(casePartyId, caseId, organizationId));
    }

    @GetMapping
    public ResponseEntity<PageResponse<CasePartyResponse>> getAllCaseParties(
            @PathVariable CaseId caseId,
            @CurrentOrganizationId OrganizationId organizationId,
            final Pageable pageable
    ) {
        return ResponseEntity.ok(casePartyService.getAllCasePartiesByOrganizationId(caseId, organizationId, pageable));
    }

    @PostMapping
    public ResponseEntity<CasePartyResponse> createCaseParty(
            @PathVariable CaseId caseId,
            @CurrentOrganizationId OrganizationId organizationId,
            @RequestBody @Valid CasePartyCreateRequest request
    ) {
        CasePartyResponse response = casePartyService.createCaseParty(caseId, organizationId, request);
        return LocationUtility.createdWithLocation(response.id(), response);
    }

    @PatchMapping("/{casePartyId}")
    public ResponseEntity<CasePartyResponse> updateCaseParty(
            @PathVariable CaseId caseId,
            @PathVariable CasePartyId casePartyId,
            @CurrentOrganizationId OrganizationId organizationId,
            @RequestBody @Valid CasePartyUpdateRequest request
    ) {
        return ResponseEntity.ok(casePartyService.updateCaseParty(casePartyId, caseId, organizationId, request));
    }

    @DeleteMapping("/{casePartyId}")
    public ResponseEntity<Void> deleteCaseParty(
            @PathVariable CaseId caseId,
            @PathVariable CasePartyId casePartyId,
            @CurrentOrganizationId OrganizationId organizationId,
            @CurrentUserId UserId deletedById,
            @RequestParam(required = false) @Size(max = DELETE_REASON_LENGTH) String deleteReason
    ) {
        casePartyService.deleteCaseParty(casePartyId, caseId, organizationId, deletedById, deleteReason);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{casePartyId}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> restoreCaseParty(
            @PathVariable CaseId caseId,
            @PathVariable CasePartyId casePartyId,
            @CurrentOrganizationId OrganizationId organizationId,
            @CurrentUserId UserId restoredById
    ) {
        casePartyService.restoreCaseParty(casePartyId, caseId, organizationId, restoredById);
        return ResponseEntity.noContent().build();
    }
}

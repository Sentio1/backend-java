package com.lisovskyi.core_service.deadline;

import com.lisovskyi.core_service.deadline.dto.request.DeadlineManualRegisterRequest;
import com.lisovskyi.core_service.deadline.dto.request.DeadlineRejectRequest;
import com.lisovskyi.core_service.deadline.dto.response.DeadlineResponse;
import com.sentio.shared.dto.PageResponse;
import com.sentio.shared.entity.id.case_.CaseId;
import com.sentio.shared.entity.id.case_event.CaseEventId;
import com.sentio.shared.entity.id.deadline.DeadlineId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import com.sentio.shared.entity.id.user.UserId;
import com.sentio.shared.security.CurrentOrganizationId;
import com.sentio.shared.security.CurrentUserId;
import com.sentio.shared.web.LocationUtility;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cases/{caseId}/deadlines")
@RequiredArgsConstructor
public class DeadlineController {

    private final DeadlineService deadlineService;

    @GetMapping
    public ResponseEntity<PageResponse<DeadlineResponse>> getAllDeadlines(
            @PathVariable CaseId caseId,
            @CurrentOrganizationId OrganizationId organizationId,
            @RequestParam(required = false) CaseEventId triggeringEventId,
            final Pageable pageable) {
        return ResponseEntity.ok(deadlineService.getAllDeadlines(caseId, organizationId, triggeringEventId, pageable));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'LAWYER')")
    public ResponseEntity<DeadlineResponse> createDeadline(
            @PathVariable CaseId caseId,
            @CurrentOrganizationId OrganizationId organizationId,
            @CurrentUserId UserId createdById,
            @RequestBody @Valid DeadlineManualRegisterRequest request
    ) {
        DeadlineResponse deadlineResponse = deadlineService.createManualDeadline(caseId, organizationId, createdById, request);
        return LocationUtility.createdWithLocation(deadlineResponse.id(), deadlineResponse);
    }

    @PatchMapping("/{deadlineId}/reject")
    @PreAuthorize("hasAnyRole('OWNER', 'LAWYER')")
    public ResponseEntity<DeadlineResponse> rejectDeadline(
            @PathVariable CaseId caseId,
            @PathVariable DeadlineId deadlineId,
            @CurrentOrganizationId OrganizationId organizationId,
            @CurrentUserId UserId rejectedById,
            @RequestBody @Valid DeadlineRejectRequest request) {
        return ResponseEntity.ok(deadlineService.rejectDeadline(caseId, deadlineId, organizationId, rejectedById, request));
    }
}

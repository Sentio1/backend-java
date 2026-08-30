package com.lisovskyi.core_service.deadline;

import com.lisovskyi.core_service.deadline.dto.response.DeadlineResponse;
import com.sentio.shared.dto.PageResponse;
import com.sentio.shared.entity.id.case_.CaseId;
import com.sentio.shared.entity.id.case_event.CaseEventId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import com.sentio.shared.security.CurrentOrganizationId;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
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
}

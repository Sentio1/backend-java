package com.lisovskyi.core_service.case_event_occurred_at_history;

import com.lisovskyi.core_service.case_event.dto.response.CaseEventOccurredAtHistoryResponse;
import com.sentio.shared.dto.PageResponse;
import com.sentio.shared.entity.id.case_.CaseId;
import com.sentio.shared.entity.id.case_event.CaseEventId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import com.sentio.shared.security.CurrentOrganizationId;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cases/{caseId}/case-event/{eventId}/occurred-at-history")
@RequiredArgsConstructor
public class CaseEventOccurredAtHistoryController {

    private final CaseEventOccurredAtHistoryService caseEventOccurredAtHistoryService;

    @GetMapping
    public ResponseEntity<PageResponse<CaseEventOccurredAtHistoryResponse>> getAllCaseEventOccurredAtHistories(
            @PathVariable CaseId caseId,
            @PathVariable CaseEventId eventId,
            @CurrentOrganizationId OrganizationId organizationId,
            final Pageable pageable) {
        return ResponseEntity.ok(
                caseEventOccurredAtHistoryService.getAllCaseEventOccurredAtHistories(caseId, eventId, organizationId, pageable));
    }
}

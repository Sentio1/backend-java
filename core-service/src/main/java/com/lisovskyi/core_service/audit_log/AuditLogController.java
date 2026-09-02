package com.lisovskyi.core_service.audit_log;

import com.lisovskyi.core_service.audit_log.dto.response.AuditLogResponse;
import com.sentio.shared.dto.PageResponse;
import com.sentio.shared.entity.id.case_.CaseId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import com.sentio.shared.security.CurrentOrganizationId;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// SEN-23 AC: "Історія доступна з картки справи" - один ендпоінт на всю справу, що об'єднує
// зміни cases/case_events/deadlines (AuditLogRepository.findAllByCaseIdAndOrganizationId),
// а не окремі маршрути під кожен тип сутності.
@RestController
@RequestMapping("/cases/{caseId}/audit-log")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<PageResponse<AuditLogResponse>> getCaseAuditLog(
            @PathVariable CaseId caseId,
            @CurrentOrganizationId OrganizationId organizationId,
            final Pageable pageable
    ) {
        return ResponseEntity.ok(auditLogService.getCaseAuditLog(caseId, organizationId, pageable));
    }
}

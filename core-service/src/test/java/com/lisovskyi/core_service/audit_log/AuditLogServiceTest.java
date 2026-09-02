package com.lisovskyi.core_service.audit_log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lisovskyi.core_service.audit_log.dto.response.AuditLogResponse;
import com.lisovskyi.core_service.audit_log.finder.AuditLogFinder;
import com.lisovskyi.core_service.audit_log.mapper.AuditLogMapper;
import com.lisovskyi.core_service.case_.Case;
import com.lisovskyi.core_service.case_.finder.CaseFinder;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.shared.dto.PageResponse;
import com.sentio.shared.entity.id.case_.CaseId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import com.sentio.shared.entity.id.user.UserId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
/** AuditLogServiceTest class. */
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AuditLogFinder auditLogFinder;

    @Mock
    private AuditLogMapper auditLogMapper;

    @Mock
    private CaseFinder caseFinder;

    @InjectMocks
    private AuditLogService auditLogService;

    @Test
    void log_savesAuditLogWithGivenFieldsAndCurrentTimestamp() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);

        auditLogService.log(
                OrganizationId.of(1L), EntityType.CASE, CaseId.of(10L), UserId.of(5L), "procedure", "CIVIL", "COMMERCIAL");

        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getOrganizationId()).isEqualTo(1L);
        assertThat(saved.getEntityType()).isEqualTo(EntityType.CASE);
        assertThat(saved.getEntityId()).isEqualTo(10L);
        assertThat(saved.getChangedBy()).isEqualTo(5L);
        assertThat(saved.getFieldName()).isEqualTo("procedure");
        assertThat(saved.getOldValue()).isEqualTo("CIVIL");
        assertThat(saved.getNewValue()).isEqualTo("COMMERCIAL");
        assertThat(saved.getChangedAt()).isNotNull();
    }

    @Test
    void getCaseAuditLog_verifiesCaseBelongsToOrg_thenReturnsMappedPage() {
        Case case_ = Case.builder().id(10L).build();
        AuditLog entry = AuditLog.builder()
                .id(1L)
                .organizationId(1L)
                .entityType(EntityType.CASE)
                .entityId(10L)
                .fieldName("procedure")
                .oldValue("CIVIL")
                .newValue("COMMERCIAL")
                .changedBy(5L)
                .changedAt(Instant.now())
                .build();
        AuditLogResponse response =
                new AuditLogResponse(1L, 1L, EntityType.CASE, 10L, "procedure", "CIVIL", "COMMERCIAL", 5L, Instant.now());
        Pageable pageable = Pageable.ofSize(20);

        when(caseFinder.findByIdAndOrganizationId(10L, 1L)).thenReturn(case_);
        when(auditLogFinder.findAllByCaseIdAndOrganizationId(eq(10L), eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entry)));
        when(auditLogMapper.toResponse(entry)).thenReturn(response);

        PageResponse<AuditLogResponse> result = auditLogService.getCaseAuditLog(CaseId.of(10L), OrganizationId.of(1L), pageable);

        assertThat(result.content()).containsExactly(response);
    }

    @Test
    void getCaseAuditLog_caseNotInOrg_throwsWithoutQueryingAuditLog() {
        when(caseFinder.findByIdAndOrganizationId(10L, 1L))
                .thenThrow(new ResourceNotFoundException("Case", "id", 10L));

        assertThatThrownBy(() ->
                        auditLogService.getCaseAuditLog(CaseId.of(10L), OrganizationId.of(1L), Pageable.ofSize(20)))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(auditLogFinder);
    }
}

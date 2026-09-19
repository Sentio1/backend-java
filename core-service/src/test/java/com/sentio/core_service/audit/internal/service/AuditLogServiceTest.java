package com.sentio.core_service.audit.internal.service;

import com.sentio.core_service.audit.internal.model.AuditLog;
import com.sentio.core_service.audit.internal.repository.AuditLogRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sentio.core_service.audit.api.dto.AuditLogResponse;
import com.sentio.core_service.audit.api.enums.ChangedByType;
import com.sentio.core_service.audit.api.enums.EntityType;
import com.sentio.core_service.audit.internal.mapper.AuditLogMapper;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.shared.dto.PageResponse;
import com.sentio.shared.entity.id.case_.CaseId;
import com.sentio.shared.entity.id.deadline.DeadlineId;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
/** AuditLogServiceTest class. */
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AuditLogMapper auditLogMapper;

    @InjectMocks
    private AuditLogServiceImpl auditLogService;

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
        assertThat(saved.getChangedByType()).isEqualTo(ChangedByType.USER);
        assertThat(saved.getFieldName()).isEqualTo("procedure");
        assertThat(saved.getOldValue()).isEqualTo("CIVIL");
        assertThat(saved.getNewValue()).isEqualTo("COMMERCIAL");
        assertThat(saved.getChangedAt()).isNotNull();
    }

    @Test
    void logSystemChange_savesAuditLogWithNullChangedByAndSystemType() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);

        auditLogService.logSystemChange(
                OrganizationId.of(1L), EntityType.DEADLINE, DeadlineId.of(10L), "dueOn", "2026-01-10", "2026-01-13");

        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getChangedBy()).isNull();
        assertThat(saved.getChangedByType()).isEqualTo(ChangedByType.SYSTEM);
        assertThat(saved.getFieldName()).isEqualTo("dueOn");
        assertThat(saved.getOldValue()).isEqualTo("2026-01-10");
        assertThat(saved.getNewValue()).isEqualTo("2026-01-13");
    }

    @Test
    void findCaseHistory_passesTheGivenIdsAndReturnsMappedPage() {
        AuditLog entry = AuditLog.builder()
                .id(1L)
                .organizationId(1L)
                .entityType(EntityType.CASE)
                .entityId(10L)
                .fieldName("procedure")
                .oldValue("CIVIL")
                .newValue("COMMERCIAL")
                .changedBy(5L)
                .changedByType(ChangedByType.USER)
                .changedAt(Instant.now())
                .build();
        AuditLogResponse response = new AuditLogResponse(
                1L, 1L, EntityType.CASE, 10L, "procedure", "CIVIL", "COMMERCIAL", 5L, ChangedByType.USER, Instant.now());
        Pageable pageable = Pageable.ofSize(20);

        when(auditLogRepository.findCaseHistory(1L, 10L, List.of(100L), List.of(200L), pageable))
                .thenReturn(new PageImpl<>(List.of(entry)));
        when(auditLogMapper.toResponse(entry)).thenReturn(response);

        Page<AuditLogResponse> result =
                auditLogService.findCaseHistory(1L, 10L, List.of(100L), List.of(200L), pageable);

        assertThat(result.getContent()).containsExactly(response);
    }

    // An empty IN () list is rendered differently across Hibernate versions (or rejected) - a
    // sentinel id that can never exist is passed instead.
    @Test
    void findCaseHistory_emptyEventAndDeadlineIds_areReplacedWithNeverMatchingSentinel() {
        Pageable pageable = Pageable.ofSize(20);
        when(auditLogRepository.findCaseHistory(1L, 10L, List.of(-1L), List.of(-1L), pageable))
                .thenReturn(Page.empty());

        assertThat(auditLogService.findCaseHistory(1L, 10L, List.of(), List.of(), pageable)).isEmpty();
    }
}

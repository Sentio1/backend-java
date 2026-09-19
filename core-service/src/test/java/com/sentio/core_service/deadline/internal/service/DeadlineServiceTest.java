package com.sentio.core_service.deadline.internal.service;

import com.sentio.core_service.deadline.internal.model.Deadline;
import com.sentio.core_service.deadline.internal.repository.DeadlineRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sentio.core_service.audit.api.service.AuditLogService;
import com.sentio.core_service.litigation.api.dto.TriggeringEvent;
import com.sentio.core_service.litigation.api.enums.EventCode;
import com.sentio.core_service.litigation.api.enums.ProcedureType;
import com.sentio.core_service.litigation.api.service.CaseEventService;
import com.sentio.core_service.litigation.api.service.CaseService;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import java.time.Instant;
import java.util.Optional;
import com.sentio.core_service.audit.api.enums.EntityType;
import com.sentio.core_service.deadline.internal.controller.dto.DeadlineManualRegisterRequest;
import com.sentio.core_service.deadline.internal.controller.dto.DeadlineRejectRequest;
import com.sentio.core_service.deadline.internal.controller.dto.DeadlineResponse;
import com.sentio.core_service.deadline.internal.enums.DeadlineSource;
import com.sentio.core_service.deadline.internal.enums.DeadlineStatus;
import com.sentio.core_service.deadline.internal.exception.DeadlineNotRejectableException;
import com.sentio.core_service.deadline.internal.mapper.DeadlineMapper;
import com.sentio.shared.entity.id.case_.CaseId;
import com.sentio.shared.entity.id.deadline.DeadlineId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import com.sentio.shared.entity.id.user.UserId;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openapitools.jackson.nullable.JsonNullable;

// SEN-29 AC3/AC4: createManualDeadline (ручний строк, без DeadlineRule) і rejectDeadline
// (відхилення автоматично порахованого) - обидва без жодного тесту до цього моменту.
@ExtendWith(MockitoExtension.class)
class DeadlineServiceTest {

    @Mock
    private DeadlineRepository deadlineRepository;

    @Mock
    private DeadlineMapper deadlineMapper;

    @Mock
    private CaseService caseService;

    @Mock
    private CaseEventService caseEventService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private DeadlineService deadlineService;

    private static final CaseId CASE_ID = CaseId.of(1L);
    private static final OrganizationId ORG_ID = OrganizationId.of(5001L);

    private DeadlineResponse response(long id) {
        return DeadlineResponse.builder().id(id).build();
    }

    // ─── createManualDeadline ────────────────────────────────────────────

    @Test
    void createManualDeadline_withoutTriggeringEvent_buildsStandaloneDeadline_withoutRule() {
        DeadlineManualRegisterRequest request = new DeadlineManualRegisterRequest(
                "Підготувати документи",
                LocalDate.of(2024, 6, 1),
                LocalDate.of(2024, 6, 15),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined());
        when(deadlineRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(deadlineMapper.toResponse(any(), any())).thenReturn(response(1L));

        deadlineService.createManualDeadline(CASE_ID, ORG_ID, UserId.of(9L), request);

        ArgumentCaptor<Deadline> captor = ArgumentCaptor.forClass(Deadline.class);
        verify(deadlineRepository).save(captor.capture());
        Deadline saved = captor.getValue();
        assertThat(saved.getSource()).isEqualTo(DeadlineSource.MANUAL);
        assertThat(saved.getCreatedBy()).isEqualTo(9L);
        assertThat(saved.getRule()).isNull();
        assertThat(saved.getRuleVersion()).isNull();
        assertThat(saved.getTriggeringEventId()).isNull();
        assertThat(saved.getCaseId()).isEqualTo(1L);
        assertThat(saved.getLegalBasis()).isNull();
        assertThat(saved.getNote()).isNull();
        assertThat(saved.getTitle()).isEqualTo("Підготувати документи");
        assertThat(saved.getStartsOn()).isEqualTo(LocalDate.of(2024, 6, 1));
        assertThat(saved.getDueOn()).isEqualTo(LocalDate.of(2024, 6, 15));
        verify(caseService).assertCaseExists(1L, 5001L);
        verify(caseEventService, never()).findTriggeringEvent(anyLong(), anyLong(), anyLong());
    }

    @Test
    void createManualDeadline_withPresentOptionalFields_unwrapsJsonNullable_intoPlainEntityFields() {
        DeadlineManualRegisterRequest request = new DeadlineManualRegisterRequest(
                "Підготувати документи",
                LocalDate.of(2024, 6, 1),
                LocalDate.of(2024, 6, 15),
                JsonNullable.of("ст. 178 ЦПК України"),
                JsonNullable.of("нагадування для клієнта"),
                JsonNullable.undefined());
        when(deadlineRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(deadlineMapper.toResponse(any(), any())).thenReturn(response(1L));

        deadlineService.createManualDeadline(CASE_ID, ORG_ID, UserId.of(9L), request);

        ArgumentCaptor<Deadline> captor = ArgumentCaptor.forClass(Deadline.class);
        verify(deadlineRepository).save(captor.capture());
        // Регресія: раніше сюди передавався сам JsonNullable<String>, а не розпакований String
        // (не компілювалось би проти String-сеттера білдера, якби колись повернулось назад).
        assertThat(captor.getValue().getLegalBasis()).isEqualTo("ст. 178 ЦПК України");
        assertThat(captor.getValue().getNote()).isEqualTo("нагадування для клієнта");
    }

    @Test
    void createManualDeadline_withTriggeringEventId_looksUpAndLinksTheEvent() {
        TriggeringEvent triggeringEvent = new TriggeringEvent(
                77L, 1L, 5001L, EventCode.DECISION, Instant.parse("2024-05-31T10:00:00Z"), ProcedureType.CIVIL, null);
        DeadlineManualRegisterRequest request = new DeadlineManualRegisterRequest(
                "Підготувати документи",
                LocalDate.of(2024, 6, 1),
                LocalDate.of(2024, 6, 15),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.of(77L));
        when(caseEventService.findTriggeringEvent(77L, 1L, 5001L)).thenReturn(Optional.of(triggeringEvent));
        when(deadlineRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(deadlineMapper.toResponse(any(), any())).thenReturn(response(1L));

        deadlineService.createManualDeadline(CASE_ID, ORG_ID, UserId.of(9L), request);

        ArgumentCaptor<Deadline> captor = ArgumentCaptor.forClass(Deadline.class);
        verify(deadlineRepository).save(captor.capture());
        assertThat(captor.getValue().getTriggeringEventId()).isEqualTo(77L);
        verify(deadlineMapper).toResponse(any(), eq(EventCode.DECISION));
    }

    @Test
    void createManualDeadline_withTriggeringEventOfAnotherCase_isNotFound() {
        DeadlineManualRegisterRequest request = new DeadlineManualRegisterRequest(
                "Підготувати документи",
                LocalDate.of(2024, 6, 1),
                LocalDate.of(2024, 6, 15),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.of(77L));
        when(caseEventService.findTriggeringEvent(77L, 1L, 5001L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deadlineService.createManualDeadline(CASE_ID, ORG_ID, UserId.of(9L), request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(deadlineRepository, never()).save(any());
    }

    // ─── rejectDeadline ──────────────────────────────────────────────────

    @Test
    void rejectDeadline_pendingDeadline_marksRejected_andWritesTwoAuditEntries() {
        Deadline deadline = Deadline.builder()
                .id(10L)
                .status(DeadlineStatus.PENDING)
                .build();
        DeadlineRejectRequest request = new DeadlineRejectRequest("суд ухвалив рішення про закриття справи");
        when(deadlineRepository.findByIdAndCaseIdAndOrganizationId(10L, 1L, 5001L)).thenReturn(Optional.of(deadline));
        when(deadlineRepository.save(deadline)).thenReturn(deadline);
        when(deadlineMapper.toResponse(eq(deadline), any())).thenReturn(response(10L));

        deadlineService.rejectDeadline(
                CASE_ID, DeadlineId.of(10L), ORG_ID, UserId.of(3L), request);

        assertThat(deadline.getStatus()).isEqualTo(DeadlineStatus.REJECTED);
        assertThat(deadline.getRejectedAt()).isNotNull();
        assertThat(deadline.getRejectedBy()).isEqualTo(3L);
        assertThat(deadline.getRejectionReason()).isEqualTo("суд ухвалив рішення про закриття справи");

        verify(auditLogService)
                .log(eq(ORG_ID), eq(EntityType.DEADLINE), any(), eq(UserId.of(3L)), eq("status"), eq("PENDING"), eq("REJECTED"));
        verify(auditLogService)
                .log(eq(ORG_ID), eq(EntityType.DEADLINE), any(), eq(UserId.of(3L)), eq("rejectionReason"), eq(null),
                        eq("суд ухвалив рішення про закриття справи"));
    }

    @Test
    void rejectDeadline_alreadyDone_throwsDeadlineNotRejectableException_andNeverSavesOrAudits() {
        Deadline deadline = Deadline.builder().id(10L).status(DeadlineStatus.DONE).build();
        DeadlineRejectRequest request = new DeadlineRejectRequest("причина");
        when(deadlineRepository.findByIdAndCaseIdAndOrganizationId(10L, 1L, 5001L)).thenReturn(Optional.of(deadline));

        assertThatThrownBy(() -> deadlineService.rejectDeadline(
                        CASE_ID, DeadlineId.of(10L), ORG_ID, UserId.of(3L), request))
                .isInstanceOf(DeadlineNotRejectableException.class);

        verify(deadlineRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any(), any());
    }

    // Ідемпотентність: другий reject на вже REJECTED теж мусить відмовляти, а не мовчки
    // перезаписувати rejectedAt/rejectionReason з першої спроби.
    @Test
    void rejectDeadline_alreadyRejected_throwsDeadlineNotRejectableException() {
        Deadline deadline = Deadline.builder().id(10L).status(DeadlineStatus.REJECTED).build();
        DeadlineRejectRequest request = new DeadlineRejectRequest("причина");
        when(deadlineRepository.findByIdAndCaseIdAndOrganizationId(10L, 1L, 5001L)).thenReturn(Optional.of(deadline));

        assertThatThrownBy(() -> deadlineService.rejectDeadline(
                        CASE_ID, DeadlineId.of(10L), ORG_ID, UserId.of(3L), request))
                .isInstanceOf(DeadlineNotRejectableException.class);

        verify(deadlineRepository, times(0)).save(any());
    }
}

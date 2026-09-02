package com.lisovskyi.core_service.case_;

import static org.assertj.core.api.Assertions.assertThat;

import com.lisovskyi.core_service.TestcontainersConfiguration;
import com.lisovskyi.core_service.audit_log.AuditLog;
import com.lisovskyi.core_service.audit_log.AuditLogRepository;
import com.lisovskyi.core_service.audit_log.EntityType;
import com.lisovskyi.core_service.case_.dto.request.CaseUpdateRequest;
import com.lisovskyi.core_service.case_.enums.CaseInstance;
import com.lisovskyi.core_service.case_.enums.CaseStatus;
import com.lisovskyi.core_service.case_.enums.ProcedureType;
import com.sentio.shared.entity.id.case_.CaseId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import com.sentio.shared.entity.id.user.UserId;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

// SEN-23 AC: "Кожна зміна cases ... пише запис: хто, коли, поле, старе й нове значення" -
// наскрізно, через реальний CaseService.updateCase -> AuditLogService.log -> AuditLogRepository,
// а не лише юніт-тестом на сам AuditLogService (той перевіряє лише його власну логіку запису,
// не те, що CaseService реально викликає його з правильними значеннями на реальній зміні поля).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class CaseServiceAuditLogIT {

    @Autowired
    private CaseService caseService;

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private EntityManager entityManager;

    private void flushAndDetach() {
        entityManager.flush();
        entityManager.clear();
    }

    private CaseUpdateRequest updateProcedureOnlyRequest(ProcedureType newProcedure) {
        return new CaseUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.of(newProcedure),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined());
    }

    @Test
    void updateCase_procedureChange_writesAuditLogEntryWithOldAndNewValue() {
        Long organizationId = 6_001L;
        Case case_ = caseRepository.save(Case.builder()
                .organizationId(organizationId)
                .responsibleUserId(1L)
                .createdBy(1L)
                .title("Позов про стягнення заборгованості")
                .procedure(ProcedureType.CIVIL)
                .instance(CaseInstance.FIRST)
                .build());
        flushAndDetach();

        caseService.updateCase(
                CaseId.of(case_.getId()),
                OrganizationId.of(organizationId),
                UserId.of(7L),
                updateProcedureOnlyRequest(ProcedureType.COMMERCIAL));
        flushAndDetach();

        List<AuditLog> entries = auditLogRepository.findAll().stream()
                .filter(a -> a.getEntityType() == EntityType.CASE && a.getEntityId() == case_.getId())
                .toList();

        assertThat(entries).hasSize(1);
        AuditLog entry = entries.get(0);
        assertThat(entry.getFieldName()).isEqualTo("procedure");
        assertThat(entry.getOldValue()).isEqualTo("CIVIL");
        assertThat(entry.getNewValue()).isEqualTo("COMMERCIAL");
        assertThat(entry.getChangedBy()).isEqualTo(7L);
        assertThat(entry.getOrganizationId()).isEqualTo(organizationId);
    }

    @Test
    void updateCase_multipleFieldsChanged_writesOneAuditEntryPerField() {
        Long organizationId = 6_004L;
        Case case_ = caseRepository.save(Case.builder()
                .organizationId(organizationId)
                .responsibleUserId(1L)
                .createdBy(1L)
                .title("Позов про стягнення заборгованості")
                .procedure(ProcedureType.CIVIL)
                .instance(CaseInstance.FIRST)
                .status(CaseStatus.ACTIVE)
                .build());
        flushAndDetach();

        // Змінюємо одразу два поля поза "старою трійкою" (procedure/instance/courtId), яку
        // раніше єдину аудитували - title і status - щоб довести, що узагальнений diff у
        // updateCase справді покриває решту полів CaseUpdateRequest, а не тільки їх.
        CaseUpdateRequest request = new CaseUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.of("Нова назва справи"),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.of(CaseStatus.SUSPENDED),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined());

        caseService.updateCase(CaseId.of(case_.getId()), OrganizationId.of(organizationId), UserId.of(7L), request);
        flushAndDetach();

        List<AuditLog> entries = auditLogRepository.findAll().stream()
                .filter(a -> a.getEntityType() == EntityType.CASE && a.getEntityId() == case_.getId())
                .toList();

        assertThat(entries).hasSize(2);
        assertThat(entries)
                .extracting(AuditLog::getFieldName, AuditLog::getOldValue, AuditLog::getNewValue)
                .containsExactlyInAnyOrder(
                        Tuple.tuple("title", "Позов про стягнення заборгованості", "Нова назва справи"),
                        Tuple.tuple("status", "ACTIVE", "SUSPENDED"));
    }

    @Test
    void updateCase_procedureUnchanged_writesNoAuditLogEntry() {
        Long organizationId = 6_002L;
        Case case_ = caseRepository.save(Case.builder()
                .organizationId(organizationId)
                .responsibleUserId(1L)
                .createdBy(1L)
                .title("Позов про стягнення заборгованості")
                .procedure(ProcedureType.CIVIL)
                .instance(CaseInstance.FIRST)
                .build());
        flushAndDetach();

        // Той самий procedure, що вже є - не має вважатись зміною.
        caseService.updateCase(
                CaseId.of(case_.getId()),
                OrganizationId.of(organizationId),
                UserId.of(7L),
                updateProcedureOnlyRequest(ProcedureType.CIVIL));
        flushAndDetach();

        List<AuditLog> entries = auditLogRepository.findAll().stream()
                .filter(a -> a.getEntityType() == EntityType.CASE && a.getEntityId() == case_.getId())
                .toList();

        assertThat(entries).isEmpty();
    }
}

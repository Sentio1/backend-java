package com.lisovskyi.core_service.case_;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.lisovskyi.core_service.TestcontainersConfiguration;
import com.lisovskyi.core_service.audit_log.AuditLog;
import com.lisovskyi.core_service.audit_log.AuditLogRepository;
import com.lisovskyi.core_service.case_.dto.request.CaseUpdateRequest;
import com.lisovskyi.core_service.case_.enums.CaseInstance;
import com.lisovskyi.core_service.case_.enums.ProcedureType;
import com.sentio.shared.entity.id.case_.CaseId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import com.sentio.shared.entity.id.user.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

// SEN-23 AC: "Збій запису аудиту відкочує зміну, а не навпаки". Окремий клас (не разом із
// CaseServiceAuditLogIT) - @MockitoBean тут підміняє AuditLogRepository для ВСІХ тестів цього
// класу/контексту, а happy-path тести навмисно хочуть реальний, а не підмінений репозиторій.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class CaseServiceAuditLogFailureIT {

    @Autowired
    private CaseService caseService;

    @Autowired
    private CaseRepository caseRepository;

    @MockitoBean
    private AuditLogRepository auditLogRepository;

    @Autowired
    private EntityManager entityManager;

    private void flushAndDetach() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void updateCase_auditLogWriteFails_rollsBackTheWholeUpdate() {
        Long organizationId = 6_003L;
        Case case_ = caseRepository.save(Case.builder()
                .organizationId(organizationId)
                .responsibleUserId(1L)
                .createdBy(1L)
                .title("Позов про стягнення заборгованості")
                .procedure(ProcedureType.CIVIL)
                .instance(CaseInstance.FIRST)
                .build());
        flushAndDetach();

        when(auditLogRepository.save(any(AuditLog.class))).thenThrow(new RuntimeException("audit write boom"));

        CaseUpdateRequest request = new CaseUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.of(ProcedureType.COMMERCIAL),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined());

        assertThatThrownBy(() -> caseService.updateCase(
                        CaseId.of(case_.getId()), OrganizationId.of(organizationId), UserId.of(7L), request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("audit write boom");

        entityManager.clear();
        Case reloaded = caseRepository.findById(case_.getId()).orElseThrow();
        assertThat(reloaded.getProcedure())
                .as("procedure має лишитись незмінним - весь updateCase мав відкотитись разом зі збоєм аудиту")
                .isEqualTo(ProcedureType.CIVIL);
    }
}

package com.lisovskyi.core_service.case_.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lisovskyi.core_service.TestcontainersConfiguration;
import com.lisovskyi.core_service.case_.Case;
import com.lisovskyi.core_service.case_.CaseRepository;
import com.lisovskyi.core_service.case_.enums.CaseInstance;
import com.lisovskyi.core_service.case_.enums.ProcedureType;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.shared.entity.id.case_.CaseId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import com.sentio.shared.entity.id.user.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

// SEN-21 review fix regression: CaseService.restoreCase раніше йшов через caseFinder
// (findByIdAndOrganizationId), а @SQLRestriction("deleted_at IS NULL") на CoreEntity фільтрує
// БУДЬ-ЯКИЙ JPQL-запит до цієї сутності - тобто вже видалений рядок цим шляхом ніколи не
// знайти, і restoreCase завжди кидав ResourceNotFoundException навіть для щойно видаленого
// кейсу. Перевіряє повний цикл видалення -> відновлення саме через CaseService, а не лише
// ізольовано CaseRepository.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class CaseSoftDeleteRestoreIT {

    @Autowired
    private CaseService caseService;

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private EntityManager entityManager;

    private void flushAndDetach() {
        entityManager.flush();
        entityManager.clear();
    }

    private Case persistCase(Long organizationId) {
        Case case_ = Case.builder()
                .organizationId(organizationId)
                .responsibleUserId(1L)
                .title("Позов про стягнення заборгованості")
                .procedure(ProcedureType.CIVIL)
                .instance(CaseInstance.FIRST)
                .build();
        return caseRepository.save(case_);
    }

    @Test
    void deleteThenRestoreCase_roundTrips_andCaseBecomesVisibleAgain() {
        Long organizationId = 6_001L;
        Case case_ = persistCase(organizationId);
        flushAndDetach();

        caseService.deleteCase(CaseId.of(case_.getId()), OrganizationId.of(organizationId), UserId.of(1L), "test");
        flushAndDetach();

        assertThat(caseRepository.findByIdAndOrganizationId(case_.getId(), organizationId))
                .isEmpty();
        assertThat(caseRepository.findDeletedByIdAndOrganizationId(case_.getId(), organizationId))
                .isPresent();

        caseService.restoreCase(CaseId.of(case_.getId()), OrganizationId.of(organizationId), UserId.of(2L));
        flushAndDetach();

        var restored = caseRepository.findByIdAndOrganizationId(case_.getId(), organizationId);
        assertThat(restored).isPresent();
        assertThat(restored.get().getDeletedAt()).isNull();
        assertThat(restored.get().getRestoredBy()).isEqualTo(2L);
    }

    @Test
    void restoreCase_forACaseThatWasNeverDeleted_throwsResourceNotFound() {
        Long organizationId = 6_002L;
        Case case_ = persistCase(organizationId);
        flushAndDetach();

        assertThatThrownBy(() -> caseService.restoreCase(
                        CaseId.of(case_.getId()), OrganizationId.of(organizationId), UserId.of(1L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

package com.lisovskyi.core_service.case_party.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lisovskyi.core_service.TestcontainersConfiguration;
import com.lisovskyi.core_service.case_.Case;
import com.lisovskyi.core_service.case_.CaseRepository;
import com.lisovskyi.core_service.case_.enums.CaseInstance;
import com.lisovskyi.core_service.case_.enums.ProcedureType;
import com.lisovskyi.core_service.case_party.CaseParty;
import com.lisovskyi.core_service.case_party.CasePartyRepository;
import com.lisovskyi.core_service.case_party.CasePartyRole;
import com.lisovskyi.core_service.case_party.CasePartyService;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.shared.entity.id.case_.CaseId;
import com.sentio.shared.entity.id.case_party.CasePartyId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import com.sentio.shared.entity.id.user.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

// Той самий регресійний сценарій, що й CaseSoftDeleteRestoreIT, для CasePartyService.restoreCaseParty.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class CasePartySoftDeleteRestoreIT {

    @Autowired
    private CasePartyService casePartyService;

    @Autowired
    private CasePartyRepository casePartyRepository;

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
                .title("Позов")
                .procedure(ProcedureType.CIVIL)
                .instance(CaseInstance.FIRST)
                .build();
        return caseRepository.save(case_);
    }

    private CaseParty persistOpponentParty(Long organizationId, Case case_) {
        CaseParty party = CaseParty.builder()
                .organizationId(organizationId)
                .case_(case_)
                .role(CasePartyRole.OPPONENT)
                .opponentName("Товариство Опонент")
                .build();
        return casePartyRepository.save(party);
    }

    @Test
    void deleteThenRestoreCaseParty_roundTrips_andPartyBecomesVisibleAgain() {
        Long organizationId = 7_001L;
        Case case_ = persistCase(organizationId);
        CaseParty party = persistOpponentParty(organizationId, case_);
        flushAndDetach();

        casePartyService.deleteCaseParty(
                CasePartyId.of(party.getId()),
                CaseId.of(case_.getId()),
                OrganizationId.of(organizationId),
                UserId.of(1L),
                "test");
        flushAndDetach();

        assertThat(casePartyRepository.findByIdAndCaseIdAndOrganizationId(
                        party.getId(), case_.getId(), organizationId))
                .isEmpty();
        assertThat(casePartyRepository.findDeletedByIdAndCaseIdAndOrganizationId(
                        party.getId(), case_.getId(), organizationId))
                .isPresent();

        casePartyService.restoreCaseParty(
                CasePartyId.of(party.getId()), CaseId.of(case_.getId()), OrganizationId.of(organizationId), UserId.of(2L));
        flushAndDetach();

        var restored = casePartyRepository.findByIdAndCaseIdAndOrganizationId(
                party.getId(), case_.getId(), organizationId);
        assertThat(restored).isPresent();
        assertThat(restored.get().getDeletedAt()).isNull();
    }

    @Test
    void restoreCaseParty_forAPartyThatWasNeverDeleted_throwsResourceNotFound() {
        Long organizationId = 7_002L;
        Case case_ = persistCase(organizationId);
        CaseParty party = persistOpponentParty(organizationId, case_);
        flushAndDetach();

        assertThatThrownBy(() -> casePartyService.restoreCaseParty(
                        CasePartyId.of(party.getId()),
                        CaseId.of(case_.getId()),
                        OrganizationId.of(organizationId),
                        UserId.of(1L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

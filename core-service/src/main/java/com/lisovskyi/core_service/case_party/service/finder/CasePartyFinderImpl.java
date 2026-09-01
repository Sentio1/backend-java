package com.lisovskyi.core_service.case_party.service.finder;

import com.lisovskyi.core_service.case_.enums.CaseStatus;
import com.lisovskyi.core_service.case_party.CaseParty;
import com.lisovskyi.core_service.case_party.CasePartyRepository;
import com.sentio.shared.entity.finder.AbstractEntityFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class CasePartyFinderImpl extends AbstractEntityFinder<CaseParty, Long> implements CasePartyFinder {

    private final CasePartyRepository casePartyRepository;

    @Override
    protected JpaRepository<CaseParty, Long> getRepository() {
        return casePartyRepository;
    }

    @Override
    protected String getEntityName() {
        return "CaseParty";
    }

    @Override
    public CaseParty findByIdAndCaseIdAndOrganizationId(Long id, Long caseId, Long organizationId) {
        return findBy(id, caseId, organizationId, casePartyRepository::findByIdAndCaseIdAndOrganizationId);
    }

    @Override
    public Page<CaseParty> findAllByCaseIdAndOrganizationId(Long caseId, Long organizationId, Pageable pageable) {
        return findAll(caseId, organizationId, pageable, casePartyRepository::findAllByCaseIdAndOrganizationId);
    }

    @Override
    public boolean existsActiveCaseForClient(Long clientId, Long organizationId, Set<CaseStatus> terminalStatuses) {
        requireNonNull(clientId, organizationId, terminalStatuses);
        return casePartyRepository.existsActiveCaseForClient(clientId, organizationId, terminalStatuses);
    }
}

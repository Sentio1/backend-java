package com.lisovskyi.core_service.case_party.finder;

import com.lisovskyi.core_service.case_.enums.CaseStatus;
import com.lisovskyi.core_service.case_party.CaseParty;
import com.sentio.shared.entity.finder.EntityFinder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Set;

public interface CasePartyFinder extends EntityFinder<CaseParty, Long> {

    CaseParty findByIdAndCaseIdAndOrganizationId(Long id, Long caseId, Long organizationId);

    List<CaseParty> findAllByClientIdAndOrganizationId(Long clientId, Long organizationId);

    Page<CaseParty> findAllByCaseIdAndOrganizationId(Long caseId, Long organizationId, Pageable pageable);

    boolean existsActiveCaseForClient(Long clientId, Long organizationId, Set<CaseStatus> terminalStatuses);
}

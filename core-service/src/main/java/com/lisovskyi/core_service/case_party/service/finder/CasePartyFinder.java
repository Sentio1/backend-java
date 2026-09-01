package com.lisovskyi.core_service.case_party.service.finder;

import com.lisovskyi.core_service.case_party.CaseParty;
import com.sentio.shared.entity.finder.EntityFinder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CasePartyFinder extends EntityFinder<CaseParty, Long> {

    CaseParty findByIdAndCaseIdAndOrganizationId(Long id, Long caseId, Long organizationId);

    Page<CaseParty> findAllByCaseIdAndOrganizationId(Long caseId, Long organizationId, Pageable pageable);
}

package com.lisovskyi.core_service.case_.finder;

import com.lisovskyi.core_service.case_.Case;
import com.sentio.shared.entity.finder.EntityFinder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CaseFinder extends EntityFinder<Case, Long> {
    Page<Case> findAllByOrganizationId(Long organizationId, Pageable pageable);

    Page<Case> findAllByClientIdAndOrganizationId(Long clientId, Long organizationId, Pageable pageable);

    Page<Case> findByOrganizationIdAndCaseNumberContainingIgnoreCase(Long organizationId, String caseNumber, Pageable pageable);

    Case findByIdAndOrganizationId(Long id, Long organizationId);
}

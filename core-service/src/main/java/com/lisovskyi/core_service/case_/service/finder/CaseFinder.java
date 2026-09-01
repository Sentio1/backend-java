package com.lisovskyi.core_service.case_.service.finder;

import com.lisovskyi.core_service.case_.Case;
import com.sentio.shared.entity.finder.EntityFinder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CaseFinder extends EntityFinder<Case, Long> {

    Case findByIdAndOrganizationId(Long id, Long organizationId);

    Page<Case> findAllByOrganizationId(Long organizationId, Pageable pageable);
}

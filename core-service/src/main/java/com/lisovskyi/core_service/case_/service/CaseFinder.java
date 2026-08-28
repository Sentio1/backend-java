package com.lisovskyi.core_service.case_.service;

import com.lisovskyi.core_service.case_.Case;
import com.sentio.shared.entity.finder.EntityFinder;

public interface CaseFinder extends EntityFinder<Case, Long> {

    Case findByIdAndOrganizationId(Long id, Long organizationId);
}

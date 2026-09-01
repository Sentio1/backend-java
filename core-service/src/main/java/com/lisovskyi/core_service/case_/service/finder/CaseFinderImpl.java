package com.lisovskyi.core_service.case_.service.finder;

import com.lisovskyi.core_service.case_.Case;
import com.lisovskyi.core_service.case_.CaseRepository;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.shared.entity.finder.AbstractEntityFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CaseFinderImpl extends AbstractEntityFinder<Case, Long> implements CaseFinder {

    private final CaseRepository caseRepository;

    @Override
    public Case findByIdAndOrganizationId(Long id, Long organizationId)
            throws IllegalArgumentException, ResourceNotFoundException {
        return findBy(id, organizationId, caseRepository::findByIdAndOrganizationId);
    }

    @Override
    public Page<Case> findAllByOrganizationId(Long organizationId, Pageable pageable)
            throws IllegalArgumentException, ResourceNotFoundException {
        return findAll(organizationId, pageable, caseRepository::findAllByOrganizationId);
    }

    @Override
    protected JpaRepository<Case, Long> getRepository() {
        return caseRepository;
    }

    @Override
    protected String getEntityName() {
        return "Case";
    }
}

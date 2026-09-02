package com.lisovskyi.core_service.case_.finder;

import com.lisovskyi.core_service.case_.Case;
import com.lisovskyi.core_service.case_.CaseRepository;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.shared.entity.finder.AbstractEntityFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CaseFinderImpl extends AbstractEntityFinder<Case, Long> implements CaseFinder {

    private final CaseRepository caseRepository;

    @Override
    public Page<Case> findAllByOrganizationId(Long organizationId, Pageable pageable)
            throws IllegalArgumentException, ResourceNotFoundException {
        return findAll(organizationId, pageable, caseRepository::findAllByOrganizationId);
    }

    @Override
    public Page<Case> findAllByClientIdAndOrganizationId(Long clientId, Long organizationId, Pageable pageable)
            throws IllegalArgumentException, ResourceNotFoundException {
        return findAll(clientId, organizationId, pageable, caseRepository::findAllByClientIdAndOrganizationId);
    }

    @Override
    public Page<Case> findByOrganizationIdAndCaseNumberContainingIgnoreCase(Long organizationId, String caseNumber, Pageable pageable)
            throws IllegalArgumentException, ResourceNotFoundException {
        return findAll(organizationId, caseNumber, pageable, caseRepository::findByOrganizationIdAndCaseNumberContainingIgnoreCase);
    }

    @Override
    public Case findByIdAndOrganizationId(Long id, Long organizationId)
            throws IllegalArgumentException, ResourceNotFoundException {
        return findBy(id, organizationId, caseRepository::findByIdAndOrganizationId);
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

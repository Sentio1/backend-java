package com.lisovskyi.core_service.case_event.finder;

import com.lisovskyi.core_service.case_event.CaseEvent;
import com.lisovskyi.core_service.case_event.CaseEventRepository;
import com.lisovskyi.core_service.case_event.enums.EventCode;
import com.sentio.shared.entity.finder.AbstractEntityFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CaseEventFinderImpl extends AbstractEntityFinder<CaseEvent, Long> implements CaseEventFinder {

    private final CaseEventRepository caseEventRepository;

    @Override
    protected JpaRepository<CaseEvent, Long> getRepository() {
        return caseEventRepository;
    }

    @Override
    protected String getEntityName() {
        return "CaseEvent";
    }

    @Override
    public List<CaseEvent> findAllByCaseIdAndOrganizationId(Long caseId, Long organizationId) {
        requireNonNull(caseId, organizationId);
        return caseEventRepository.findAllByCaseIdAndOrganizationId(caseId, organizationId);
    }

    @Override
    public Page<CaseEvent> findAllByCaseIdAndOrganizationId(Long caseId, Long organizationId, EventCode eventCode, Pageable pageable) {
        requireNonNull(caseId, organizationId);
        return caseEventRepository.findAllByCaseIdAndOrganizationId(caseId, organizationId, eventCode, pageable);
    }

    @Override
    public Page<CaseEvent> findAllByCaseIdAndOrganizationId(Long caseId, Long organizationId, List<EventCode> eventCodes, Pageable pageable) {
        requireNonNull(caseId, organizationId);
        return caseEventRepository.findAllByCaseIdAndOrganizationId(caseId, organizationId, eventCodes, pageable);
    }

    @Override
    public CaseEvent findByIdAndCaseIdAndOrganizationId(Long id, Long caseId, Long organizationId) {
        return findBy(id, caseId, organizationId, caseEventRepository::findByIdAndCaseIdAndOrganizationId);
    }

    @Override
    public Optional<CaseEvent> findDeletedByIdAndCaseIdAndOrganizationId(Long id, Long caseId, Long organizationId) {
        requireNonNull(id, caseId, organizationId);
        return caseEventRepository.findDeletedByIdAndCaseIdAndOrganizationId(id, caseId, organizationId);
    }
}

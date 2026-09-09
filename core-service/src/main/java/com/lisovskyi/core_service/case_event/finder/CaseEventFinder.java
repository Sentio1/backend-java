package com.lisovskyi.core_service.case_event.finder;

import com.lisovskyi.core_service.case_event.CaseEvent;
import com.lisovskyi.core_service.case_event.enums.EventCode;
import com.sentio.shared.entity.finder.EntityFinder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface CaseEventFinder extends EntityFinder<CaseEvent, Long> {

    List<CaseEvent> findAllByCaseIdAndOrganizationId(Long caseId, Long organizationId);

    Page<CaseEvent> findAllByCaseIdAndOrganizationId(Long caseId, Long organizationId, EventCode eventCode, Pageable pageable);

    Page<CaseEvent> findAllByCaseIdAndOrganizationId(Long caseId, Long organizationId, List<EventCode> eventCodes, Pageable pageable);

    CaseEvent findByIdAndCaseIdAndOrganizationId(Long id, Long caseId, Long organizationId);

    Optional<CaseEvent> findDeletedByIdAndCaseIdAndOrganizationId(Long id, Long caseId, Long organizationId);
}

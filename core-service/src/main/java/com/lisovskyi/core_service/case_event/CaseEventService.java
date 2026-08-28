package com.lisovskyi.core_service.case_event;

import com.lisovskyi.core_service.case_.Case;
import com.lisovskyi.core_service.case_.service.CaseFinder;
import com.lisovskyi.core_service.case_event.dto.request.CaseEventAutoRegisterRequest;
import com.lisovskyi.core_service.case_event.dto.request.CaseEventManualRegisterRequest;
import com.lisovskyi.core_service.case_event.dto.response.CaseEventResponse;
import com.lisovskyi.core_service.case_event.enums.Source;
import com.lisovskyi.core_service.case_event.mapper.CaseEventMapper;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceAlreadyExistsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static com.sentio.shared.persistence.ConstraintViolations.isUniqueConstraintViolation;

@Service
@RequiredArgsConstructor
@Slf4j
public class CaseEventService {

    private final CaseEventRepository caseEventRepository;

    private final CaseEventMapper caseEventMapper;
    private final CaseFinder caseFinder;

    // Самостійно юрист створює case event
    @Transactional
    public CaseEventResponse registerCaseEvent(Long caseId, Long organizationId, Long createdBy, CaseEventManualRegisterRequest request) {
        Case case_ = caseFinder.findByIdAndOrganizationId(caseId, organizationId);

        CaseEvent caseEvent = caseEventMapper.toEntity(request, organizationId, case_);
        caseEvent.setCreatedBy(createdBy);
        caseEvent.setSource(Source.MANUAL);
        caseEvent.setRegisteredAt(Instant.now());

        CaseEvent savedCaseEvent = caseEventRepository.saveAndFlush(caseEvent);
        deadlineEngine(savedCaseEvent);
        return caseEventMapper.toResponse(savedCaseEvent);
    }


    // Приходить з Go сервісу
    @Transactional
    public CaseEventResponse registerRegistryCaseEvent(Long caseId, Long organizationId, CaseEventAutoRegisterRequest request) {
        Case case_ = caseFinder.findByIdAndOrganizationId(caseId, organizationId);

        CaseEvent caseEvent = caseEventMapper.toEntity(request, organizationId, case_);
        caseEvent.setSource(Source.REGISTRY);
        caseEvent.setRegistryDocumentId(request.registryDocumentId());
        caseEvent.setRegisteredAt(request.registeredAt());

        try {
            CaseEvent savedCaseEvent = caseEventRepository.saveAndFlush(caseEvent);
            deadlineEngine(savedCaseEvent);
            return caseEventMapper.toResponse(savedCaseEvent);
        } catch (DataIntegrityViolationException e) {
            if (isUniqueConstraintViolation(e, "uq_case_events_case_registry_document")) {
                throw new ResourceAlreadyExistsException(
                        "Case event with the same registryDocumentId already exists for this case");
            }
            throw e;
        }
    }

    private void deadlineEngine(CaseEvent caseEvent) {

    }
}

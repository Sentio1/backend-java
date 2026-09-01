package com.lisovskyi.core_service.case_.service;

import com.lisovskyi.core_service.case_.Case;
import com.lisovskyi.core_service.case_.CaseRepository;
import com.lisovskyi.core_service.case_.dto.request.CaseCreateRequest;
import com.lisovskyi.core_service.case_.dto.request.CaseUpdateRequest;
import com.lisovskyi.core_service.case_.dto.response.CaseResponse;
import com.lisovskyi.core_service.case_.mapper.CaseMapper;
import com.lisovskyi.core_service.case_.service.finder.CaseFinder;
import com.lisovskyi.core_service.case_event.CaseEvent;
import com.lisovskyi.core_service.case_event.CaseEventRepository;
import com.lisovskyi.core_service.court.Court;
import com.lisovskyi.core_service.court.CourtRepository;
import com.lisovskyi.core_service.deadline_engine.DeadlineEngine;
import com.lisovskyi.core_service.entity.SoftDeleteManager;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.shared.dto.PageResponse;
import com.sentio.shared.entity.id.case_.CaseId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import com.sentio.shared.entity.id.user.UserId;
import com.sentio.shared.util.JsonNullableSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CaseService {

    private final CaseRepository caseRepository;
    private final CourtRepository courtRepository;
    private final CaseEventRepository caseEventRepository;
    private final CaseFinder caseFinder;
    private final CaseMapper caseMapper;

    private final DeadlineEngine deadlineEngine;
    private final SoftDeleteManager softDeleteManager;

    @Transactional(readOnly = true)
    public CaseResponse getCaseByIdAndOrganizationId(CaseId caseId, OrganizationId organizationId) {
        return caseMapper.toResponse(caseFinder.findByIdAndOrganizationId(caseId.id(), organizationId.id()));
    }

    @Transactional(readOnly = true)
    public PageResponse<CaseResponse> getAllCasesByOrganizationId(OrganizationId organizationId, Pageable pageable) {
        return PageResponse.of(
                caseFinder.findAllByOrganizationId(organizationId.id(), pageable)
                        .map(caseMapper::toResponse)
        );
    }

    @Transactional
    public CaseResponse createCase(OrganizationId organizationId, UserId createdById, CaseCreateRequest request) {
        Case case_ = caseMapper.toEntity(request, organizationId.id(), createdById.id());
        return caseMapper.toResponse(caseRepository.save(case_));
    }

    @Transactional
    public CaseResponse updateCase(CaseId caseId, OrganizationId organizationId, CaseUpdateRequest request) {
        Case case_ = caseFinder.findByIdAndOrganizationId(caseId.id(), organizationId.id());

        boolean procedureChanged = request.procedure().map(p -> p != case_.getProcedure()).orElse(false);
        boolean instanceChanged = request.instance().map(i -> i != case_.getInstance()).orElse(false);

        caseMapper.updateEntityFromRequest(request, case_);

        // setIfPresent, а не orElse(null)+isPresent(): "courtId": null у тілі запиту (present,
        // значення null) - це явне "зняти суд", а відсутнє поле - "не чіпати" (той самий
        // present/absent-розрізняючий патерн, що й JsonNullableSupport.setIfPresent усюди на
        // PATCH-request-обробці). courtRepository.findById(null) інакше впав би на
        // present-але-null кейсі замість коректно занулити зв'язок.
        JsonNullableSupport.setIfPresent(request.courtId(), courtId -> {
            if (courtId == null) {
                case_.setCourt(null);
                return;
            }
            Court court = courtRepository.findById(courtId)
                    .orElseThrow(() -> new ResourceNotFoundException("Court", "id", courtId));
            case_.setCourt(court);
        });

        Case savedCase = caseRepository.save(case_);

        if (procedureChanged || instanceChanged) {
            List<CaseEvent> caseEvents = caseEventRepository.findAllByCaseIdAndOrganizationId(caseId.id(), organizationId.id());
            deadlineEngine.recalcAllDeadlines(caseEvents);
        }

        return caseMapper.toResponse(savedCase);
    }

    @Transactional
    public void deleteCase(CaseId caseId, OrganizationId organizationId, UserId deletedById, String deleteReason) {
        Case case_ = caseFinder.findByIdAndOrganizationId(caseId.id(), organizationId.id());
        softDeleteManager.deleteEntity(case_, caseRepository, deletedById.id(), deleteReason);
    }

    @Transactional
    public void restoreCase(CaseId caseId, OrganizationId organizationId, UserId restoredById) {
        // caseFinder не годиться тут: він іде через звичайний findByIdAndOrganizationId, який
        // @SQLRestriction("deleted_at IS NULL") на CoreEntity фільтрує для БУДЬ-ЯКОГО JPQL-запиту -
        // тобто вже видалений рядок цим шляхом ніколи не знайти. Потрібен нативний запит в обхід
        // рестрикції (як і ClientRepository.findDeletedByIdAndOrganizationId у ClientService).
        Case case_ = caseRepository
                .findDeletedByIdAndOrganizationId(caseId.id(), organizationId.id())
                .orElseThrow(() -> new ResourceNotFoundException("Case", "id", caseId));
        softDeleteManager.restoreEntity(case_, caseRepository, restoredById.id());
    }
}

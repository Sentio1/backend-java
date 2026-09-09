package com.lisovskyi.core_service.case_;

import com.lisovskyi.core_service.audit_log.AuditLogService;
import com.lisovskyi.core_service.audit_log.enums.EntityType;
import com.lisovskyi.core_service.case_.dto.request.CaseCreateRequest;
import com.lisovskyi.core_service.case_.dto.request.CaseUpdateRequest;
import com.lisovskyi.core_service.case_.dto.response.CaseResponse;
import com.lisovskyi.core_service.case_.mapper.CaseMapper;
import com.lisovskyi.core_service.case_.finder.CaseFinder;
import com.lisovskyi.core_service.case_event.CaseEvent;
import com.lisovskyi.core_service.case_event.finder.CaseEventFinder;
import com.lisovskyi.core_service.court.Court;
import com.lisovskyi.core_service.court.finder.CourtFinder;
import com.lisovskyi.core_service.deadline_processor.DeadlineGenerator;
import com.lisovskyi.core_service.entity.SoftDeleteManager;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.shared.dto.PageResponse;
import com.sentio.shared.entity.id.case_.CaseId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import com.sentio.shared.entity.id.user.UserId;
import com.sentio.shared.util.JsonNullableSupport;
import org.openapitools.jackson.nullable.JsonNullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class CaseService {

    private final CaseRepository caseRepository;
    private final CourtFinder courtFinder;
    private final CaseEventFinder caseEventFinder;
    private final CaseFinder caseFinder;
    private final CaseMapper caseMapper;

    private final DeadlineGenerator deadlineGenerator;
    private final SoftDeleteManager softDeleteManager;
    private final AuditLogService auditLogService;

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
    public CaseResponse updateCase(CaseId caseId, OrganizationId organizationId, UserId changedById, CaseUpdateRequest request) {
        Case case_ = caseFinder.findByIdAndOrganizationId(caseId.id(), organizationId.id());

        var oldCaseNumber = case_.getCaseNumber();
        var oldInternalNumber = case_.getInternalNumber();
        var oldTitle = case_.getTitle();
        var oldProcedure = case_.getProcedure();
        var oldInstance = case_.getInstance();
        var oldStatus = case_.getStatus();
        var oldJudgeName = case_.getJudgeName();
        var oldResponsibleUserId = case_.getResponsibleUserId();
        var oldOpenedAt = case_.getOpenedAt();
        var oldClosedAt = case_.getClosedAt();
        var oldRegistryWatchEnabled = case_.isRegistryWatchEnabled();

        caseMapper.updateEntityFromRequest(request, case_);

        auditFieldChangeIfPresent(
                organizationId, caseId, changedById, request.caseNumber(), "caseNumber", oldCaseNumber, case_.getCaseNumber());
        auditFieldChangeIfPresent(
                organizationId,
                caseId,
                changedById,
                request.internalNumber(),
                "internalNumber",
                oldInternalNumber,
                case_.getInternalNumber());

        auditFieldChangeIfPresent(organizationId, caseId, changedById, request.title(), "title", oldTitle, case_.getTitle());

        auditFieldChangeIfPresent(
                organizationId, caseId, changedById, request.procedure(), "procedure", oldProcedure, case_.getProcedure());

        auditFieldChangeIfPresent(
                organizationId, caseId, changedById, request.instance(), "instance", oldInstance, case_.getInstance());

        auditFieldChangeIfPresent(organizationId, caseId, changedById, request.status(), "status", oldStatus, case_.getStatus());

        auditFieldChangeIfPresent(
                organizationId, caseId, changedById, request.judgeName(), "judgeName", oldJudgeName, case_.getJudgeName());

        auditFieldChangeIfPresent(
                organizationId,
                caseId,
                changedById,
                request.responsibleUserId(),
                "responsibleUserId",
                oldResponsibleUserId,
                case_.getResponsibleUserId());

        auditFieldChangeIfPresent(
                organizationId, caseId, changedById, request.openedAt(), "openedAt", oldOpenedAt, case_.getOpenedAt());

        auditFieldChangeIfPresent(
                organizationId, caseId, changedById, request.closedAt(), "closedAt", oldClosedAt, case_.getClosedAt());

        auditFieldChangeIfPresent(
                organizationId,
                caseId,
                changedById,
                request.registryWatchEnabled(),
                "registryWatchEnabled",
                oldRegistryWatchEnabled,
                case_.isRegistryWatchEnabled());

        boolean procedureChanged = !Objects.equals(oldProcedure, case_.getProcedure());
        boolean instanceChanged = !Objects.equals(oldInstance, case_.getInstance());

        // setIfPresent, а не orElse(null)+isPresent(): "courtId": null у тілі запиту (present,
        // значення null) - це явне "зняти суд", а відсутнє поле - "не чіпати" (той самий
        // present/absent-розрізняючий патерн, що й JsonNullableSupport.setIfPresent усюди на
        // PATCH-request-обробці). courtRepository.findById(null) інакше впав би на
        // present-але-null кейсі замість коректно занулити зв'язок.
        JsonNullableSupport.setIfPresent(request.courtId(), courtId -> {
            Long oldCourtId = case_.getCourt() != null ? case_.getCourt().getId() : null;

            if (courtId == null) {
                case_.setCourt(null);
            } else {
                Court court = courtFinder.findById(courtId);
                case_.setCourt(court);
            }

            // !Objects.equals, не Objects.equals: пишемо аудит-запис, коли courtId РЕАЛЬНО
            // змінився, а не коли лишився тим самим (інверсія тут раніше писала "зміну" саме
            // коли нічого не змінювалось, і мовчала на справжні зміни суду).
            if (!Objects.equals(oldCourtId, courtId)) {
                auditLogService.log(organizationId, EntityType.CASE, caseId, changedById,
                        "courtId", String.valueOf(oldCourtId), String.valueOf(courtId));
            }
        });

        Case savedCase = caseRepository.save(case_);

        if (procedureChanged || instanceChanged) {
            List<CaseEvent> caseEvents = caseEventFinder.findAllByCaseIdAndOrganizationId(caseId.id(), organizationId.id());
            deadlineGenerator.recalcAllDeadlines(caseEvents, changedById.id());
        }

        return caseMapper.toResponse(savedCase);
    }

    // requestField.isPresent(): пишемо аудит лише для полів, які реально БУЛИ в тілі PATCH-запиту
    // (не для всіх 9+ полів Case на кожен виклик updateCase) - "не присутнє" з JsonNullable і так
    // не могло змінити значення (applyPresentFields його не чіпав), тож перевірка тут суто
    // страхує від хибних спрацювань, якщо колись oldValue/newValue порахують по-іншому.
    private void auditFieldChangeIfPresent(
            OrganizationId organizationId,
            CaseId caseId,
            UserId changedById,
            JsonNullable<?> requestField,
            String fieldName,
            Object oldValue,
            Object newValue) {
        if (requestField.isPresent() && !Objects.equals(oldValue, newValue)) {
            auditLogService.log(
                    organizationId,
                    EntityType.CASE,
                    caseId,
                    changedById,
                    fieldName,
                    oldValue == null ? null : oldValue.toString(),
                    newValue == null ? null : newValue.toString());
        }
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

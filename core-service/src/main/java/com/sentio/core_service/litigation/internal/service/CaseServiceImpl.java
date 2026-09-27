package com.sentio.core_service.litigation.internal.service;

import com.sentio.core_service.litigation.internal.model.Case;
import com.sentio.core_service.litigation.internal.repository.CaseRepository;

import com.sentio.core_service.audit.api.dto.AuditLogResponse;
import com.sentio.core_service.audit.api.service.AuditLogService;
import com.sentio.core_service.audit.api.enums.EntityType;
import com.sentio.core_service.litigation.internal.controller.dto.CaseCreateRequest;
import com.sentio.core_service.litigation.internal.controller.dto.CaseUpdateRequest;
import com.sentio.core_service.litigation.api.dto.CaseResponse;
import com.sentio.core_service.litigation.internal.mapper.CaseMapper;
import com.sentio.core_service.court.api.dto.CourtResponse;
import com.sentio.core_service.court.api.service.CourtService;
import com.sentio.core_service.litigation.api.service.CaseService;
import com.sentio.core_service.litigation.api.spi.CaseEventDeadlines;
import com.sentio.core_service.litigation.internal.model.CaseEvent;
import com.sentio.core_service.litigation.internal.exception.CaseNotFoundException;
import com.sentio.core_service.litigation.internal.mapper.CaseEventMapper;
import com.sentio.core_service.litigation.internal.repository.CaseEventRepository;
import com.sentio.core_service.common.model.SoftDeleteManager;
import com.sentio.shared.dto.PageResponse;
import com.sentio.shared.entity.id.case_.CaseId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import com.sentio.shared.entity.id.user.UserId;
import com.sentio.shared.util.JsonNullableSupport;
import org.openapitools.jackson.nullable.JsonNullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CaseServiceImpl implements CaseService {

    private final CaseRepository caseRepository;
    private final CaseEventRepository caseEventRepository;
    private final CaseMapper caseMapper;
    private final CaseEventMapper caseEventMapper;

    private final CourtService courtService;
    private final CaseEventDeadlines caseEventDeadlines;
    private final SoftDeleteManager softDeleteManager;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public CaseResponse getCaseByIdAndOrganizationId(CaseId caseId, OrganizationId organizationId) {
        return toResponse(findCase(caseId.id(), organizationId.id()));
    }

    @Transactional(readOnly = true)
    public PageResponse<CaseResponse> getAllCasesByOrganizationId(OrganizationId organizationId, Pageable pageable) {
        return PageResponse.of(toResponses(caseRepository.findAllByOrganizationId(organizationId.id(), pageable)));
    }

    @Transactional
    public CaseResponse createCase(OrganizationId organizationId, UserId createdById, CaseCreateRequest request) {
        Case case_ = caseMapper.toEntity(request, organizationId.id(), createdById.id());
        if (case_.getCourtId() != null) {
            // 404 for an unknown court instead of a raw FK violation (500) on insert.
            courtService.getById(case_.getCourtId());
        }
        return toResponse(caseRepository.save(case_));
    }

    @Transactional
    public CaseResponse updateCase(CaseId caseId, OrganizationId organizationId, UserId changedById, CaseUpdateRequest request) {
        Case case_ = findCase(caseId.id(), organizationId.id());

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
            Long oldCourtId = case_.getCourtId();

            if (courtId != null) {
                // Validates the court exists (404 otherwise) - Case only keeps its id.
                courtService.getById(courtId);
            }
            case_.setCourtId(courtId);

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
            List<CaseEvent> caseEvents =
                    caseEventRepository.findAllByCaseIdAndOrganizationId(caseId.id(), organizationId.id());
            log.info("Case {} procedure/instance changed - recalculating deadlines of {} event(s)",
                    caseId, caseEvents.size());
            caseEvents.forEach(caseEvent ->
                    caseEventDeadlines.regenerate(caseEventMapper.toTriggeringEvent(caseEvent), changedById.id()));
        }

        return toResponse(savedCase);
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
        Case case_ = findCase(caseId.id(), organizationId.id());
        softDeleteManager.deleteEntity(case_, caseRepository, deletedById.id(), deleteReason);
    }

    @Transactional
    public void restoreCase(CaseId caseId, OrganizationId organizationId, UserId restoredById) {
        // findCase не годиться тут: він іде через звичайний findByIdAndOrganizationId, який
        // @SQLRestriction("deleted_at IS NULL") на CoreEntity фільтрує для БУДЬ-ЯКОГО JPQL-запиту -
        // тобто вже видалений рядок цим шляхом ніколи не знайти. Потрібен нативний запит в обхід
        // рестрикції (як і ClientRepository.findDeletedByIdAndOrganizationId у ClientService).
        Case case_ = caseRepository
                .findDeletedByIdAndOrganizationId(caseId.id(), organizationId.id())
                .orElseThrow(() -> new CaseNotFoundException(caseId.id()));
        softDeleteManager.restoreEntity(case_, caseRepository, restoredById.id());
    }

    // "Історія доступна з картки справи" (SEN-23 AC): зміни самої справи + її подій + її
    // дедлайнів однією стрічкою. Які події/дедлайни належать справі - знає цей модуль (події) і
    // deadline (через SPI), а audit отримує лише їхні id.
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getCaseAuditLog(CaseId caseId, OrganizationId organizationId, Pageable pageable) {
        assertCaseExists(caseId.id(), organizationId.id());

        List<Long> caseEventIds = caseEventRepository.findIdsByCaseIdAndOrganizationId(caseId.id(), organizationId.id());
        List<Long> deadlineIds = caseEventDeadlines.findDeadlineIdsByCase(caseId.id(), organizationId.id());

        return PageResponse.of(auditLogService.findCaseHistory(
                organizationId.id(), caseId.id(), caseEventIds, deadlineIds, pageable));
    }

    // ---- CaseService (api) --------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public void assertCaseExists(long caseId, long organizationId) {
        if (!caseRepository.existsByIdAndOrganizationId(caseId, organizationId)) {
            throw new CaseNotFoundException(caseId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<CaseResponse> searchByCaseNumber(long organizationId, String query, int limit) {
        return toResponses(caseRepository.findByOrganizationIdAndCaseNumberContainingIgnoreCase(
                organizationId, query, Pageable.ofSize(limit))).getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CaseResponse> findAllByClient(long clientId, long organizationId, int limit) {
        return toResponses(caseRepository.findAllByClientIdAndOrganizationId(
                clientId, organizationId, Pageable.ofSize(limit))).getContent();
    }

    // ---- helpers ------------------------------------------------------------------------

    Case findCase(long caseId, long organizationId) {
        return caseRepository.findByIdAndOrganizationId(caseId, organizationId)
                .orElseThrow(() -> new CaseNotFoundException(caseId));
    }

    private CaseResponse toResponse(Case case_) {
        CourtResponse court = case_.getCourtId() != null ? courtService.findById(case_.getCourtId()).orElse(null) : null;
        return caseMapper.toResponse(case_, court);
    }

    // One court lookup for the whole page instead of one per case.
    private Page<CaseResponse> toResponses(Page<Case> cases) {
        Set<Long> courtIds = cases.stream()
                .map(Case::getCourtId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, CourtResponse> courts = courtService.findAllByIds(courtIds);
        return cases.map(case_ -> caseMapper.toResponse(
                case_, case_.getCourtId() != null ? courts.get(case_.getCourtId()) : null));
    }
}

package com.sentio.core_service.litigation.internal.service;

import com.sentio.core_service.litigation.internal.model.CaseParty;
import com.sentio.core_service.litigation.internal.repository.CasePartyRepository;

import static com.sentio.shared.persistence.ConstraintViolations.isUniqueConstraintViolation;

import com.sentio.core_service.litigation.internal.model.Case;
import com.sentio.core_service.litigation.internal.controller.dto.CasePartyCreateRequest;
import com.sentio.core_service.litigation.internal.controller.dto.CasePartyUpdateRequest;
import com.sentio.core_service.litigation.internal.controller.dto.CasePartyResponse;
import com.sentio.core_service.litigation.internal.mapper.CasePartyMapper;
import com.sentio.core_service.client.api.dto.ClientResponse;
import com.sentio.core_service.client.api.service.ClientService;
import com.sentio.core_service.litigation.internal.exception.CaseNotFoundException;
import com.sentio.core_service.litigation.internal.exception.CasePartyNotFoundException;
import com.sentio.core_service.litigation.internal.repository.CaseRepository;
import com.sentio.core_service.common.model.SoftDeleteManager;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceAlreadyExistsException;
import com.sentio.shared.dto.PageResponse;
import com.sentio.shared.entity.id.case_.CaseId;
import com.sentio.shared.entity.id.case_party.CasePartyId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import com.sentio.shared.entity.id.user.UserId;
import com.sentio.shared.util.JsonNullableSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Slf4j
@RequiredArgsConstructor
public class CasePartyService {

    private final CasePartyRepository casePartyRepository;
    private final CaseRepository caseRepository;
    private final ClientService clientService;

    private final CasePartyMapper casePartyMapper;
    private final SoftDeleteManager softDeleteManager;

    @Transactional(readOnly = true)
    public CasePartyResponse getCaseParty(CasePartyId casePartyId, CaseId caseId, OrganizationId organizationId) {
        return toResponse(findCaseParty(casePartyId, caseId, organizationId));
    }

    @Transactional(readOnly = true)
    public PageResponse<CasePartyResponse> getAllCasePartiesByOrganizationId(CaseId caseId, OrganizationId organizationId, Pageable pageable) {
        return PageResponse.of(toResponses(
                casePartyRepository.findAllByCaseIdAndOrganizationId(caseId.id(), organizationId.id(), pageable),
                organizationId.id()));
    }

    @Transactional
    public CasePartyResponse createCaseParty(CaseId caseId, OrganizationId organizationId, CasePartyCreateRequest request) {
        Case case_ = caseRepository.findByIdAndOrganizationId(caseId.id(), organizationId.id())
                .orElseThrow(() -> new CaseNotFoundException(caseId.id()));

        CaseParty caseParty = casePartyMapper.toEntity(request, case_);

        // На create clientId, якщо присутнє, завжди несе реальне значення - CasePartyCreateRequest
        // XOR-валідація (isValidPartySource) вже не пускає сюди "clientId присутнє, але null":
        // тоді hasClient=false й запит валідний лише з opponentName. orElseThrow, а не orElse(null)
        // як було: невідомий/чужий clientId інакше мовчки лишав caseParty без client І без
        // opponentName - падало на case_parties_client_xor_opponent_check замість чистого 404.
        Long clientId = request.clientId().orElse(null);
        if (clientId != null) {
            // 404 for an unknown/foreign client - CaseParty only keeps its id.
            clientService.getByIdAndOrganizationId(clientId, organizationId.id());
            caseParty.setClientId(clientId);
        }

        return toResponse(saveCaseParty(caseParty));
    }

    @Transactional
    public CasePartyResponse updateCaseParty(CasePartyId casePartyId, CaseId caseId, OrganizationId organizationId, CasePartyUpdateRequest request) {
        CaseParty caseParty = findCaseParty(casePartyId, caseId, organizationId);

        casePartyMapper.updateEntityFromRequest(request, caseParty);

        JsonNullableSupport.setIfPresent(request.clientId(), clientId -> {
            if (clientId != null) {
                clientService.getByIdAndOrganizationId(clientId, organizationId.id());
            }
            caseParty.setClientId(clientId);
        });

        boolean hasClient = caseParty.getClientId() != null;
        boolean hasOpponentName = StringUtils.hasText(caseParty.getOpponentName());
        if (hasClient == hasOpponentName) {
            throw new IllegalArgumentException(
                    "Either 'clientId' must be set for an existing client OR 'opponentName' for an external opponent, but not both.");
        }

        return toResponse(saveCaseParty(caseParty));
    }

    // case_parties_case_id_client_id_role_idx / case_parties_case_id_primary_idx (V5) - той
    // самий "перевести брудний DataIntegrityViolationException у чистий 409" підхід, що й
    // ClientService для uq_clients_org_rnokpp/uq_clients_org_edrpou. saveAndFlush, а не save:
    // порушення інакше спливло б лише на flush наприкінці транзакції, поза цим catch-блоком.
    private CaseParty saveCaseParty(CaseParty caseParty) {
        try {
            return casePartyRepository.saveAndFlush(caseParty);
        } catch (DataIntegrityViolationException e) {
            if (isUniqueConstraintViolation(
                    e, "case_parties_case_id_client_id_role_idx", "case_parties_case_id_primary_idx")) {
                log.warn("Failed to save case party: duplicate party/role or primary conflict for caseId={}",
                        caseParty.getCase_() != null ? caseParty.getCase_().getId() : null);
                throw new ResourceAlreadyExistsException(
                        "This client already has this role in the case, or another party is already marked primary");
            }
            throw e;
        }
    }

    @Transactional
    public void deleteCaseParty(CasePartyId casePartyId, CaseId caseId, OrganizationId organizationId, UserId deletedById, String deleteReason) {
        CaseParty caseParty = findCaseParty(casePartyId, caseId, organizationId);
        softDeleteManager.deleteEntity(caseParty, casePartyRepository, deletedById.id(), deleteReason);
    }

    @Transactional
    public void restoreCaseParty(CasePartyId casePartyId, CaseId caseId, OrganizationId organizationId, UserId restoredById) {
        // findCaseParty не годиться: @SQLRestriction("deleted_at IS NULL") фільтрує будь-який
        // JPQL-запит до CaseParty, тому звичайний findByIdAndCaseIdAndOrganizationId ніколи не
        // знайде вже видалений рядок - потрібен нативний запит в обхід рестрикції.
        CaseParty caseParty = casePartyRepository
                .findDeletedByIdAndCaseIdAndOrganizationId(casePartyId.id(), caseId.id(), organizationId.id())
                .orElseThrow(() -> new CasePartyNotFoundException(casePartyId.id()));
        softDeleteManager.restoreEntity(caseParty, casePartyRepository, restoredById.id());
    }

    private CaseParty findCaseParty(CasePartyId casePartyId, CaseId caseId, OrganizationId organizationId) {
        return casePartyRepository
                .findByIdAndCaseIdAndOrganizationId(casePartyId.id(), caseId.id(), organizationId.id())
                .orElseThrow(() -> new CasePartyNotFoundException(casePartyId.id()));
    }

    private CasePartyResponse toResponse(CaseParty caseParty) {
        ClientResponse client = caseParty.getClientId() != null
                ? clientService.getByIdAndOrganizationId(caseParty.getClientId(), caseParty.getOrganizationId())
                : null;
        return casePartyMapper.toResponse(caseParty, client);
    }

    // One client lookup for the whole page instead of one per party.
    private Page<CasePartyResponse> toResponses(Page<CaseParty> caseParties, long organizationId) {
        Set<Long> clientIds = caseParties.stream()
                .map(CaseParty::getClientId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ClientResponse> clients = clientService.findAllByIdsAndOrganizationId(clientIds, organizationId);
        return caseParties.map(caseParty -> casePartyMapper.toResponse(
                caseParty, caseParty.getClientId() != null ? clients.get(caseParty.getClientId()) : null));
    }
}

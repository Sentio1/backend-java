package com.lisovskyi.core_service.case_party;

import static com.sentio.shared.persistence.ConstraintViolations.isUniqueConstraintViolation;

import com.lisovskyi.core_service.case_.Case;
import com.lisovskyi.core_service.case_.finder.CaseFinder;
import com.lisovskyi.core_service.case_party.dto.request.CasePartyCreateRequest;
import com.lisovskyi.core_service.case_party.dto.request.CasePartyUpdateRequest;
import com.lisovskyi.core_service.case_party.dto.response.CasePartyResponse;
import com.lisovskyi.core_service.case_party.mapper.CasePartyMapper;
import com.lisovskyi.core_service.case_party.finder.CasePartyFinder;
import com.lisovskyi.core_service.client.Client;
import com.lisovskyi.core_service.client.finder.ClientFinder;
import com.lisovskyi.core_service.entity.SoftDeleteManager;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceAlreadyExistsException;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.shared.dto.PageResponse;
import com.sentio.shared.entity.id.case_.CaseId;
import com.sentio.shared.entity.id.case_party.CasePartyId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import com.sentio.shared.entity.id.user.UserId;
import com.sentio.shared.util.JsonNullableSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Slf4j
@RequiredArgsConstructor
public class CasePartyService {

    private final CasePartyRepository casePartyRepository;
    private final CasePartyFinder casePartyFinder;
    private final ClientFinder clientFinder;

    private final CasePartyMapper casePartyMapper;
    private final SoftDeleteManager softDeleteManager;
    private final CaseFinder caseFinder;

    @Transactional(readOnly = true)
    public CasePartyResponse getCaseParty(CasePartyId casePartyId, CaseId caseId, OrganizationId organizationId) {
        return casePartyMapper.toResponse(
                casePartyFinder.findByIdAndCaseIdAndOrganizationId(casePartyId.id(), caseId.id(), organizationId.id())
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<CasePartyResponse> getAllCasePartiesByOrganizationId(CaseId caseId, OrganizationId organizationId, Pageable pageable) {
        return PageResponse.of(
            casePartyFinder.findAllByCaseIdAndOrganizationId(caseId.id(), organizationId.id(), pageable)
                    .map(casePartyMapper::toResponse)
        );
    }

    @Transactional
    public CasePartyResponse createCaseParty(CaseId caseId, OrganizationId organizationId, CasePartyCreateRequest request) {
        Case case_ = caseFinder.findByIdAndOrganizationId(caseId.id(), organizationId.id());

        CaseParty caseParty = casePartyMapper.toEntity(request, case_);

        // На create clientId, якщо присутнє, завжди несе реальне значення - CasePartyCreateRequest
        // XOR-валідація (isValidPartySource) вже не пускає сюди "clientId присутнє, але null":
        // тоді hasClient=false й запит валідний лише з opponentName. orElseThrow, а не orElse(null)
        // як було: невідомий/чужий clientId інакше мовчки лишав caseParty без client І без
        // opponentName - падало на case_parties_client_xor_opponent_check замість чистого 404.
        Long clientId = request.clientId().orElse(null);
        if (clientId != null) {
            Client client = clientFinder.findByIdAndOrganizationId(clientId, organizationId.id());
            caseParty.setClient(client);
        }

        return casePartyMapper.toResponse(saveCaseParty(caseParty));
    }

    @Transactional
    public CasePartyResponse updateCaseParty(CasePartyId casePartyId, CaseId caseId, OrganizationId organizationId, CasePartyUpdateRequest request) {
        CaseParty caseParty = casePartyFinder.findByIdAndCaseIdAndOrganizationId(casePartyId.id(), caseId.id(), organizationId.id());

        casePartyMapper.updateEntityFromRequest(request, caseParty);

        JsonNullableSupport.setIfPresent(request.clientId(), clientId -> {
            if (clientId == null) {
                caseParty.setClient(null);
                return;
            }
            Client client = clientFinder.findByIdAndOrganizationId(clientId, organizationId.id());
            caseParty.setClient(client);
        });

        boolean hasClient = caseParty.getClient() != null;
        boolean hasOpponentName = StringUtils.hasText(caseParty.getOpponentName());
        if (hasClient == hasOpponentName) {
            throw new IllegalArgumentException(
                    "Either 'clientId' must be set for an existing client OR 'opponentName' for an external opponent, but not both.");
        }

        return casePartyMapper.toResponse(saveCaseParty(caseParty));
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
        CaseParty caseParty = casePartyFinder.findByIdAndCaseIdAndOrganizationId(casePartyId.id(), caseId.id(), organizationId.id());
        softDeleteManager.deleteEntity(caseParty, casePartyRepository, deletedById.id(), deleteReason);
    }

    @Transactional
    public void restoreCaseParty(CasePartyId casePartyId, CaseId caseId, OrganizationId organizationId, UserId restoredById) {
        // casePartyFinder не годиться: @SQLRestriction("deleted_at IS NULL") фільтрує будь-який
        // JPQL-запит до CaseParty, тому звичайний findByIdAndCaseIdAndOrganizationId ніколи не
        // знайде вже видалений рядок - потрібен нативний запит в обхід рестрикції.
        CaseParty caseParty = casePartyRepository
                .findDeletedByIdAndCaseIdAndOrganizationId(casePartyId.id(), caseId.id(), organizationId.id())
                .orElseThrow(() -> new ResourceNotFoundException("CaseParty", "id", casePartyId));
        softDeleteManager.restoreEntity(caseParty, casePartyRepository, restoredById.id());
    }
}

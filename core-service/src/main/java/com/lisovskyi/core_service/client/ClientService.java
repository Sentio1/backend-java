package com.lisovskyi.core_service.client;

import com.lisovskyi.core_service.case_.enums.CaseStatus;
import com.lisovskyi.core_service.case_party.CasePartyRepository;
import com.lisovskyi.core_service.client.dto.request.ClientCreateRequest;
import com.lisovskyi.core_service.client.dto.request.ClientUpdateRequest;
import com.lisovskyi.core_service.client.dto.response.ClientResponse;
import com.lisovskyi.core_service.client.exception.ClientHasActiveCasesException;
import com.lisovskyi.core_service.client.mapper.ClientMapper;
import com.lisovskyi.core_service.entity.SoftDeletable;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceAlreadyExistsException;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.shared.dto.PageResponse;
import com.sentio.shared.entity.id.client.ClientId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import com.sentio.shared.entity.id.user.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.sentio.shared.persistence.ConstraintViolations.isUniqueConstraintViolation;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientService implements SoftDeletable {

    private final ClientRepository clientRepository;
    private final CasePartyRepository casePartyRepository;

    private final ClientMapper clientMapper;

    private static final Set<CaseStatus> TERMINAL_CASE_STATUSES = Arrays.stream(CaseStatus.values())
            .filter(CaseStatus::isTerminal)
            .collect(Collectors.toUnmodifiableSet());

    @Transactional(readOnly = true)
    public PageResponse<ClientResponse> getAllClients(OrganizationId organizationId, Pageable pageable) {
        log.debug("Fetching all clients for orgId: {}", organizationId);
        return PageResponse.of(clientRepository
                .findAllByOrganizationId(organizationId.id(), pageable)
                .map(clientMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<ClientResponse> getAllDeletedClients(OrganizationId organizationId, Pageable pageable) {
        log.debug("Fetching all deleted clients for orgId: {}", organizationId);
        return PageResponse.of(clientRepository
                .findAllDeletedByOrganizationId(organizationId.id(), pageable)
                .map(clientMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public ClientResponse getClientById(ClientId clientId, OrganizationId organizationId) {
        log.debug("Fetching client clientId: {} for orgId: {}", clientId, organizationId);
        return clientRepository
                .findByIdAndOrganizationId(clientId.id(), organizationId.id())
                .map(clientMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Client", "id", clientId));
    }

    @Transactional(readOnly = true)
    public PageResponse<ClientResponse> searchClient(OrganizationId organizationId, String query, Pageable pageable) {
        log.debug("Searching clients in orgId: {} with query: '{}'", organizationId, query);
        return PageResponse.of(
                clientRepository.searchClient(organizationId.id(), query, pageable).map(clientMapper::toResponse));
    }

    @Transactional
    public List<ClientResponse> createManyClients(
            OrganizationId organizationId, UserId createdById, List<ClientCreateRequest> requests) {
        log.debug("Creating {} clients for orgId: {}", requests.size(), organizationId);
        List<ClientResponse> result = new ArrayList<>();
        requests.forEach(request -> result.add(createClientNotTransactional(organizationId, createdById, request)));
        log.info("Successfully created {} clients for orgId: {}", result.size(), organizationId);
        return result;
    }

    @Transactional
    public ClientResponse createClient(OrganizationId organizationId, UserId createdById, ClientCreateRequest request) {
        log.debug("Attempting to create single client for orgId: {}", organizationId);
        return createClientNotTransactional(organizationId, createdById, request);
    }

    @Transactional
    public ClientResponse updateClient(ClientId clientId, OrganizationId organizationId, ClientUpdateRequest request) {
        log.debug("Attempting to update client id: {} for orgId: {}", clientId, organizationId);
        Client client = clientRepository
                .findByIdAndOrganizationId(clientId.id(), organizationId.id())
                .orElseThrow(() -> new ResourceNotFoundException("Client", "id", clientId));

        String rnokpp = request.rnokpp().orElse(null);
        String edrpou = request.edrpou().orElse(null);

        if (request.rnokpp().isPresent() || request.edrpou().isPresent()) {
            assertUniqueTaxIds(organizationId.id(), rnokpp, edrpou, clientId.id());
        }

        clientMapper.updateEntityFromRequest(request, client);
        Client savedClient = clientRepository.save(client);
        log.info("Successfully updated client id: {}", clientId);
        return clientMapper.toResponse(savedClient);
    }

    @Transactional
    public void deleteClient(ClientId clientId, OrganizationId organizationId, UserId deletedById, String deleteReason) {
        log.debug("Attempting to delete client clientId: {} for orgId: {}", clientId, organizationId);
        Client client = clientRepository
                .findByIdAndOrganizationId(clientId.id(), organizationId.id())
                .orElseThrow(() -> new ResourceNotFoundException("Client", "id", clientId));

        // замість архівування, просто кидаю помилку, щоб не "видалити" клієнта
        if (casePartyRepository.existsActiveCaseForClient(clientId.id(), organizationId.id(), TERMINAL_CASE_STATUSES)) {
            throw new ClientHasActiveCasesException(clientId);
        }

        deleteEntity(client, deletedById.id(), deleteReason);
        clientRepository.save(client);
        log.info("Successfully deleted client clientId: {}", clientId);
    }

    @Transactional
    public void restoreClient(ClientId clientId, OrganizationId organizationId, UserId restoredById) {
        log.debug("Attempting to restore client clientId: {} for orgId: {}", clientId, organizationId);
        Client client = clientRepository
                .findDeletedByIdAndOrganizationId(clientId.id(), organizationId.id())
                .orElseThrow(() -> new ResourceNotFoundException("Client", "id", clientId));

        restoreEntity(client, restoredById.id());
        clientRepository.save(client);
        log.info("Successfully restored client clientId: {}", clientId);
    }

    private ClientResponse createClientNotTransactional(
            OrganizationId organizationId, UserId createdById, ClientCreateRequest request) {
        assertUniqueTaxIds(
                organizationId.id(), request.rnokpp().orElse(null), request.edrpou().orElse(null), null);

        Client client = clientMapper.toEntity(request, organizationId.id(), createdById.id());

        try {
            Client savedClient = clientRepository.saveAndFlush(client);
            log.info("Successfully created client id: {} in orgId: {}", savedClient.getId(), organizationId);
            return clientMapper.toResponse(savedClient);
        } catch (DataIntegrityViolationException e) {
            if (isUniqueConstraintViolation(e, "uq_clients_org_rnokpp", "uq_clients_org_edrpou")) {
                log.warn("Failed to create client: Duplicate RNOKPP/EDRPOU in orgId: {}", organizationId);
                throw new ResourceAlreadyExistsException(
                        "Client with the same RNOKPP or EDRPOU already exists in this organization");
            }
            log.error("Data integrity violation while creating client in orgId: {}", organizationId, e);
            throw e;
        }
    }

    // rnokpp/edrpou унікальні в межах організації лише серед активних (не видалених) клієнтів -
    // узгоджено з частковими unique-індексами в V11__clients_unique_tax_ids.sql.
    private void assertUniqueTaxIds(Long organizationId, String rnokpp, String edrpou, Long excludeId) {
        if (StringUtils.hasText(rnokpp)
                && clientRepository.existsByOrganizationIdAndRnokppAndIdNot(organizationId, rnokpp, excludeId)) {
            log.warn("Validation failed: Client with RNOKPP {} already exists in orgId: {}", rnokpp, organizationId);
            throw new ResourceAlreadyExistsException("Client", "rnokpp", rnokpp);
        }
        if (StringUtils.hasText(edrpou)
                && clientRepository.existsByOrganizationIdAndEdrpouAndIdNot(organizationId, edrpou, excludeId)) {
            log.warn("Validation failed: Client with EDRPOU {} already exists in orgId: {}", edrpou, organizationId);
            throw new ResourceAlreadyExistsException("Client", "edrpou", edrpou);
        }
    }
}

package com.sentio.core_service.client.internal.service;

import com.sentio.core_service.client.api.enums.ClientType;
import com.sentio.core_service.client.internal.model.Client;
import com.sentio.core_service.client.internal.repository.ClientRepository;

import static com.sentio.shared.persistence.ConstraintViolations.isUniqueConstraintViolation;

import com.sentio.core_service.client.internal.controller.dto.ClientCreateRequest;
import com.sentio.core_service.client.internal.controller.dto.ClientUpdateRequest;
import com.sentio.core_service.client.api.dto.ClientResponse;
import com.sentio.core_service.client.internal.exception.ClientHasActiveCasesException;
import com.sentio.core_service.client.internal.mapper.ClientMapper;
import com.sentio.core_service.common.model.SoftDeleteManager;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceAlreadyExistsException;
import com.sentio.shared.dto.PageResponse;
import com.sentio.shared.entity.id.client.ClientId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import com.sentio.shared.entity.id.user.UserId;
import com.sentio.core_service.client.api.service.ClientService;
import com.sentio.core_service.client.api.spi.ClientActiveCasesChecker;
import com.sentio.core_service.client.internal.exception.ClientNotFoundException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientServiceImpl implements ClientService {

    private final ClientRepository clientRepository;
    private final ClientActiveCasesChecker clientActiveCasesChecker;
    private final ClientMapper clientMapper;

    private final SoftDeleteManager softDeleteManager;

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
        return clientMapper.toResponse(
                findClient(clientId.id(), organizationId.id()));
    }

    @Transactional(readOnly = true)
    public PageResponse<ClientResponse> searchClient(OrganizationId organizationId, String query, Pageable pageable) {
        // Сирий query не логуємо, оскільки пошук може виконуватись за чутливими даними (РНОКПП, паспорт)
        log.debug("Searching clients in orgId: {}", organizationId);
        return PageResponse.of(clientRepository
                .searchClient(organizationId.id(), query, pageable)
                .map(clientMapper::toResponse));
    }

    @Transactional
    public List<ClientResponse> createManyClients(
            OrganizationId organizationId, UserId createdById, List<ClientCreateRequest> requests) {
        log.debug("Creating {} clients for orgId: {}", requests.size(), organizationId);

        List<Client> entities = requests.stream()
                .map(request -> {
                    assertUniqueTaxIds(
                            organizationId.id(),
                            request.rnokpp().orElse(null),
                            request.edrpou().orElse(null),
                            null);
                    return clientMapper.toEntity(request, organizationId.id(), createdById.id());
                })
                .toList();

        try {
            List<ClientResponse> result = clientRepository.saveAllAndFlush(entities).stream()
                    .map(clientMapper::toResponse)
                    .toList();

            log.info("Successfully created {} clients for orgId: {}", result.size(), organizationId);
            return result;
        } catch (DataIntegrityViolationException e) {
            if (isUniqueConstraintViolation(e, "uq_clients_org_rnokpp", "uq_clients_org_edrpou")) {
                log.warn("Failed to create clients batch: Duplicate tax identifier in orgId: {}", organizationId);
                throw new ResourceAlreadyExistsException(
                        "Client with the same RNOKPP or EDRPOU already exists in this organization");
            }
            log.error("Data integrity violation while batch creating clients in orgId: {}", organizationId, e);
            throw e;
        }
    }

    @Transactional
    public ClientResponse createClient(OrganizationId organizationId, UserId createdById, ClientCreateRequest request) {
        log.debug("Attempting to create single client for orgId: {}", organizationId);
        return createClientNotTransactional(organizationId, createdById, request);
    }

    @Transactional
    public ClientResponse updateClient(ClientId clientId, OrganizationId organizationId, ClientUpdateRequest request) {
        log.debug("Attempting to update client id: {} for orgId: {}", clientId, organizationId);

        Client client = findClient(clientId.id(), organizationId.id());

        if (request.activities().isPresent() && client.getType() != ClientType.SOLE_TRADER) {
            throw new IllegalArgumentException("Activities can only be modified for SOLE_TRADER clients");
        }

        String rnokpp = request.rnokpp().orElse(null);
        String edrpou = request.edrpou().orElse(null);

        if (request.rnokpp().isPresent() || request.edrpou().isPresent()) {
            assertUniqueTaxIds(organizationId.id(), rnokpp, edrpou, clientId.id());
        }

        clientMapper.updateEntityFromRequest(request, client);

        try {
            Client savedClient = clientRepository.saveAndFlush(client);
            log.info("Successfully updated client id: {} for orgId: {}", clientId, organizationId);
            return clientMapper.toResponse(savedClient);
        } catch (DataIntegrityViolationException e) {
            if (isUniqueConstraintViolation(e, "uq_clients_org_rnokpp", "uq_clients_org_edrpou")) {
                log.warn(
                        "Failed to update client id: {}: Duplicate tax identifier in orgId: {}",
                        clientId,
                        organizationId);
                throw new ResourceAlreadyExistsException(
                        "Client with the same RNOKPP or EDRPOU already exists in this organization");
            }
            log.error(
                    "Data integrity violation while updating client id: {} in orgId: {}", clientId, organizationId, e);
            throw e;
        }
    }

    @Transactional
    public void deleteClient(
            ClientId clientId, OrganizationId organizationId, UserId deletedById, String deleteReason) {
        log.debug("Attempting to delete client clientId: {} for orgId: {}", clientId, organizationId);
        Client client = findClient(clientId.id(), organizationId.id());

        if (clientActiveCasesChecker.hasActiveCases(clientId.id(), organizationId.id())) {
            throw new ClientHasActiveCasesException(clientId);
        }

        softDeleteManager.deleteEntity(client, deletedById.id(), deleteReason);
        clientRepository.save(client);
        log.info("Successfully deleted client clientId: {} in orgId: {}", clientId, organizationId);
    }

    @Transactional
    public void restoreClient(ClientId clientId, OrganizationId organizationId, UserId restoredById) {
        log.debug("Attempting to restore client clientId: {} for orgId: {}", clientId, organizationId);
        Client client = clientRepository
                .findDeletedByIdAndOrganizationId(clientId.id(), organizationId.id())
                .orElseThrow(() -> new ClientNotFoundException(clientId.id()));

        assertUniqueTaxIds(organizationId.id(), client.getRnokpp(), client.getEdrpou(), clientId.id());

        softDeleteManager.restoreEntity(client, restoredById.id());

        try {
            clientRepository.saveAndFlush(client);
            log.info("Successfully restored client clientId: {} in orgId: {}", clientId, organizationId);
        } catch (DataIntegrityViolationException e) {
            if (isUniqueConstraintViolation(e, "uq_clients_org_rnokpp", "uq_clients_org_edrpou")) {
                log.warn(
                        "Failed to restore client id: {}: Duplicate tax identifier in orgId: {}",
                        clientId,
                        organizationId);
                throw new ResourceAlreadyExistsException(
                        "Client with the same RNOKPP or EDRPOU already exists in this organization");
            }
            log.error(
                    "Data integrity violation while restoring client id: {} in orgId: {}", clientId, organizationId, e);
            throw e;
        }
    }

    private ClientResponse createClientNotTransactional(
            OrganizationId organizationId, UserId createdById, ClientCreateRequest request) {
        assertUniqueTaxIds(
                organizationId.id(),
                request.rnokpp().orElse(null),
                request.edrpou().orElse(null),
                null);

        Client client = clientMapper.toEntity(request, organizationId.id(), createdById.id());

        try {
            Client savedClient = clientRepository.saveAndFlush(client);
            log.info("Successfully created client id: {} in orgId: {}", savedClient.getId(), organizationId);
            return clientMapper.toResponse(savedClient);
        } catch (DataIntegrityViolationException e) {
            if (isUniqueConstraintViolation(e, "uq_clients_org_rnokpp", "uq_clients_org_edrpou")) {
                log.warn("Failed to create client: Duplicate tax identifier in orgId: {}", organizationId);
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
                && clientRepository.existsActiveByOrganizationIdAndRnokpp(organizationId, rnokpp, excludeId)) {
            log.warn("Validation failed: Client with duplicate RNOKPP already exists in orgId: {}", organizationId);
            throw new ResourceAlreadyExistsException("Client", "rnokpp", "DUPLICATE_VALUE");
        }

        if (StringUtils.hasText(edrpou)
                && clientRepository.existsActiveByOrganizationIdAndEdrpou(organizationId, edrpou, excludeId)) {
            log.warn("Validation failed: Client with duplicate EDRPOU already exists in orgId: {}", organizationId);
            throw new ResourceAlreadyExistsException("Client", "edrpou", "DUPLICATE_VALUE");
        }
    }

    private Client findClient(long clientId, long organizationId) {
        return clientRepository.findByIdAndOrganizationId(clientId, organizationId)
                .orElseThrow(() -> new ClientNotFoundException(clientId));
    }

    // ---- ClientService (api) - lookups for other modules -------------------------------

    @Override
    @Transactional(readOnly = true)
    public ClientResponse getByIdAndOrganizationId(long clientId, long organizationId) {
        return clientMapper.toResponse(findClient(clientId, organizationId));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ClientResponse> findAllByIdsAndOrganizationId(Collection<Long> clientIds, long organizationId) {
        if (clientIds.isEmpty()) {
            return Map.of();
        }
        return clientRepository.findAllByIdInAndOrganizationId(clientIds, organizationId).stream()
                .map(clientMapper::toResponse)
                .collect(Collectors.toMap(ClientResponse::id, Function.identity()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClientResponse> search(long organizationId, String query, int limit) {
        return clientRepository.searchClient(organizationId, query, Pageable.ofSize(limit)).stream()
                .map(clientMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ClientResponse> findByOrganizationIdAndRnokpp(long organizationId, String rnokpp) {
        return clientRepository.findByOrganizationIdAndRnokpp(organizationId, rnokpp).map(clientMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ClientResponse> findByOrganizationIdAndEdrpou(long organizationId, String edrpou) {
        return clientRepository.findByOrganizationIdAndEdrpou(organizationId, edrpou).map(clientMapper::toResponse);
    }
}

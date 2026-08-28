package com.lisovskyi.core_service.client;

import static com.sentio.shared.persistence.ConstraintViolations.isUniqueConstraintViolation;

import com.lisovskyi.core_service.client.dto.request.ClientCreateRequest;
import com.lisovskyi.core_service.client.dto.request.ClientUpdateRequest;
import com.lisovskyi.core_service.client.dto.response.ClientResponse;
import com.lisovskyi.core_service.client.mapper.ClientMapper;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceAlreadyExistsException;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.shared.dto.PageResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
public class ClientService {

    private final ClientRepository clientRepository;
    private final ClientMapper clientMapper;

    @Transactional(readOnly = true)
    public PageResponse<ClientResponse> getAllClients(Long organizationId, Pageable pageable) {
        return PageResponse.of(clientRepository
                .findAllByOrganizationId(organizationId, pageable)
                .map(clientMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<ClientResponse> getAllDeletedClients(Long organizationId, Pageable pageable) {
        return PageResponse.of(clientRepository
                .findAllDeletedByOrganizationId(organizationId, pageable)
                .map(clientMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public ClientResponse getClientById(Long id, Long organizationId) {
        return clientRepository
                .findByIdAndOrganizationId(id, organizationId)
                .map(clientMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Client", "id", id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ClientResponse> searchClient(Long organizationId, String query, Pageable pageable) {
        return PageResponse.of(
                clientRepository.searchClient(organizationId, query, pageable).map(clientMapper::toResponse));
    }

    @Transactional
    public List<ClientResponse> createManyClients(
            Long organizationId, Long createdById, List<ClientCreateRequest> requests) {
        List<ClientResponse> result = new ArrayList<>();
        requests.forEach(request -> result.add(createClientNotTransactional(organizationId, createdById, request)));

        return result;
    }

    @Transactional
    public ClientResponse createClient(Long organizationId, Long createdById, ClientCreateRequest request) {
        return createClientNotTransactional(organizationId, createdById, request);
    }

    @Transactional
    public ClientResponse updateClient(Long id, Long organizationId, ClientUpdateRequest request) {
        Client client = clientRepository
                .findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", "id", id));

        String rnokpp = request.rnokpp().orElse(null);
        String edrpou = request.edrpou().orElse(null);

        if (request.rnokpp().isPresent() || request.edrpou().isPresent()) {
            assertUniqueTaxIds(organizationId, rnokpp, edrpou, id);
        }

        clientMapper.updateEntityFromRequest(request, client);
        return clientMapper.toResponse(clientRepository.save(client));
    }

    @Transactional
    public void deleteClient(Long id, Long organizationId, Long deletedById, String deleteReason) {
        Client client = clientRepository
                .findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", "id", id));

        client.setRestoredAt(null);
        client.setRestoredBy(null);

        client.setDeletedAt(Instant.now());
        client.setDeletedBy(deletedById);
        client.setDeleteReason(deleteReason);

        clientRepository.save(client);
    }

    @Transactional
    public void restoreClient(Long id, Long organizationId, Long restoredById) {
        Client client = clientRepository
                .findDeletedByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", "id", id));

        client.setDeletedAt(null);
        client.setDeletedBy(null);
        client.setDeleteReason(null);

        client.setRestoredAt(Instant.now());
        client.setRestoredBy(restoredById);

        clientRepository.save(client);
    }

    private ClientResponse createClientNotTransactional(
            Long organizationId, Long createdById, ClientCreateRequest request) {
        assertUniqueTaxIds(
                organizationId, request.rnokpp().orElse(null), request.edrpou().orElse(null), null);

        Client client = clientMapper.toEntity(request, organizationId, createdById);

        try {
            Client savedClient = clientRepository.saveAndFlush(client);
            return clientMapper.toResponse(savedClient);
        } catch (DataIntegrityViolationException e) {
            if (isUniqueConstraintViolation(e, "uq_clients_org_rnokpp", "uq_clients_org_edrpou")) {
                throw new ResourceAlreadyExistsException(
                        "Client with the same RNOKPP or EDRPOU already exists in this organization");
            }
            throw e;
        }
    }

    // rnokpp/edrpou унікальні в межах організації лише серед активних (не видалених) клієнтів -
    // узгоджено з частковими unique-індексами в V11__clients_unique_tax_ids.sql.
    private void assertUniqueTaxIds(Long organizationId, String rnokpp, String edrpou, Long excludeId) {
        if (StringUtils.hasText(rnokpp)
                && clientRepository.existsByOrganizationIdAndRnokppAndIdNot(organizationId, rnokpp, excludeId)) {
            throw new ResourceAlreadyExistsException("Client", "rnokpp", rnokpp);
        }
        if (StringUtils.hasText(edrpou)
                && clientRepository.existsByOrganizationIdAndEdrpouAndIdNot(organizationId, edrpou, excludeId)) {
            throw new ResourceAlreadyExistsException("Client", "edrpou", edrpou);
        }
    }
}

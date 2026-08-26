package com.lisovskyi.core_service.client;

import static com.sentio.shared.persistence.ConstraintViolations.isUniqueConstraintViolation;

import com.lisovskyi.core_service.client.dto.request.ClientCreateRequest;
import com.lisovskyi.core_service.client.dto.response.ClientResponse;
import com.lisovskyi.core_service.client.mapper.ClientMapper;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceAlreadyExistsException;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.shared.dto.PageResponse;
import java.time.Instant;
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
    public ClientResponse createClient(Long organizationId, Long createdById, ClientCreateRequest request) {
        assertUniqueTaxIds(organizationId, request.rnokpp(), request.edrpou());

        Client client = clientMapper.toEntity(request);

        client.setOrganizationId(organizationId);
        client.setCreatedBy(createdById);

        try {
            return clientMapper.toResponse(clientRepository.save(client));
        } catch (DataIntegrityViolationException e) {
            if (isUniqueConstraintViolation(e, "uq_clients_org_rnokpp", "uq_clients_org_edrpou")) {
                throw new ResourceAlreadyExistsException(
                        "Client with the same RNOKPP or EDRPOU already exists in this organization");
            }
            throw e;
        }
    }

    @Transactional
    public void deleteClient(Long id, Long organizationId, Long deletedById, String deleteReason) {
        Client client = clientRepository
                .findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", "id", id));

        client.setDeletedAt(Instant.now());
        client.setDeletedBy(deletedById);
        client.setDeleteReason(deleteReason);

        clientRepository.save(client);
    }

    // rnokpp/edrpou унікальні в межах організації лише серед активних (не видалених) клієнтів -
    // узгоджено з частковими unique-індексами в V11__clients_unique_tax_ids.sql.
    private void assertUniqueTaxIds(Long organizationId, String rnokpp, String edrpou) {
        if (StringUtils.hasText(rnokpp) && clientRepository.existsByOrganizationIdAndRnokpp(organizationId, rnokpp)) {
            throw new ResourceAlreadyExistsException("Client", "rnokpp", rnokpp);
        }
        if (StringUtils.hasText(edrpou) && clientRepository.existsByOrganizationIdAndEdrpou(organizationId, edrpou)) {
            throw new ResourceAlreadyExistsException("Client", "edrpou", edrpou);
        }
    }
}

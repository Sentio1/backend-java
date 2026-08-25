package com.lisovskyi.core_service.client;

import com.lisovskyi.core_service.client.dto.request.ClientCreateRequest;
import com.lisovskyi.core_service.client.dto.response.ClientResponse;
import com.lisovskyi.core_service.client.mapper.ClientMapper;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.shared.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientService {

    private final ClientRepository clientRepository;

    private final ClientMapper clientMapper;

    @Transactional(readOnly = true)
    public PageResponse<ClientResponse> getAllClients(Long organizationId, Pageable pageable) {
        return PageResponse.of(
                clientRepository.findAllByOrganizationId(organizationId, pageable)
                        .map(clientMapper::toResponse)
        );
    }

    @Transactional(readOnly = true)
    public ClientResponse getClientById(Long id, Long organizationId) {
        return clientRepository.findByIdAndOrganizationId(id, organizationId)
                .map(clientMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Client", "id", id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ClientResponse> searchClient(Long organizationId, String query, Pageable pageable) {
        return PageResponse.of(
                clientRepository.searchClient(organizationId, query, pageable)
                        .map(clientMapper::toResponse)
        );
    }

    @Transactional
    public ClientResponse createClient(Long organizationId, Long createdById, ClientCreateRequest request) {
        Client client = clientMapper.toEntity(request);
        client.setOrganizationId(organizationId);
        client.setCreatedBy(createdById);

        return clientMapper.toResponse(clientRepository.save(client));
    }

    @Transactional
    public void deleteClient(Long id, Long organizationId, Long deletedById, String deleteReason) {
        Client client = clientRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", "id", id));

        client.setDeletedAt(Instant.now());
        client.setDeletedBy(deletedById);
        client.setDeleteReason(deleteReason);

        clientRepository.save(client);
    }

}

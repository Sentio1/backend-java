package com.lisovskyi.core_service.client;

import com.lisovskyi.core_service.client.dto.request.ClientCreateRequest;
import com.lisovskyi.core_service.client.dto.request.ClientUpdateRequest;
import com.lisovskyi.core_service.client.dto.response.ClientResponse;
import com.sentio.shared.dto.PageResponse;
import com.sentio.shared.entity.id.client.ClientId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import com.sentio.shared.entity.id.user.UserId;
import com.sentio.shared.security.CurrentOrganizationId;
import com.sentio.shared.security.CurrentUserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

import static com.lisovskyi.core_service.entity.CoreEntityConstants.DELETE_REASON_LENGTH;

@RestController
@RequestMapping("/clients")
@RequiredArgsConstructor
@Validated
public class ClientController {

    private final ClientService clientService;

    @GetMapping
    public ResponseEntity<PageResponse<ClientResponse>> getAllClients(
            @CurrentOrganizationId OrganizationId organizationId,
            @RequestParam(required = false) String query,
            final Pageable pageable) {
        if (StringUtils.hasText(query)) {
            return ResponseEntity.ok(clientService.searchClient(organizationId, query, pageable));
        }
        return ResponseEntity.ok(clientService.getAllClients(organizationId, pageable));
    }

    @GetMapping("/deleted")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResponse<ClientResponse>> getAllDeletedClients(
            @CurrentOrganizationId OrganizationId organizationId, final Pageable pageable) {
        return ResponseEntity.ok(clientService.getAllDeletedClients(organizationId, pageable));
    }

    @GetMapping("/{clientId}")
    public ResponseEntity<ClientResponse> getClientById(
            @PathVariable ClientId clientId, @CurrentOrganizationId OrganizationId organizationId) {
        return ResponseEntity.ok(clientService.getClientById(clientId, organizationId));
    }

    @PostMapping
    public ResponseEntity<ClientResponse> createClient(
            @CurrentOrganizationId OrganizationId organizationId,
            @CurrentUserId UserId createdById,
            @RequestBody @Valid ClientCreateRequest request) {
        ClientResponse response = clientService.createClient(organizationId, createdById, request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @PostMapping("/batch")
    public ResponseEntity<List<ClientResponse>> createManyClients(
            @CurrentOrganizationId OrganizationId organizationId,
            @CurrentUserId UserId createdById,
            @RequestBody List<@Valid ClientCreateRequest> requests) {
        List<ClientResponse> response = clientService.createManyClients(organizationId, createdById, requests);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{clientId}")
    public ResponseEntity<ClientResponse> updateClient(
            @PathVariable ClientId clientId,
            @CurrentOrganizationId OrganizationId organizationId,
            @RequestBody @Valid ClientUpdateRequest request) {
        return ResponseEntity.ok(clientService.updateClient(clientId, organizationId, request));
    }

    @DeleteMapping("/{clientId}")
    public ResponseEntity<Void> deleteClient(
            @PathVariable ClientId clientId,
            @CurrentOrganizationId OrganizationId organizationId,
            @CurrentUserId UserId deletedById,
            @RequestParam(required = false) @Size(max = DELETE_REASON_LENGTH) String deleteReason) {
        clientService.deleteClient(clientId, organizationId, deletedById, deleteReason);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{clientId}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> restoreClient(
            @PathVariable ClientId clientId, @CurrentOrganizationId OrganizationId organizationId, @CurrentUserId UserId restoredById) {
        clientService.restoreClient(clientId, organizationId, restoredById);
        return ResponseEntity.noContent().build();
    }
}

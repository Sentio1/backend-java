package com.lisovskyi.core_service.client;

import static com.lisovskyi.core_service.entity.CoreEntityConstants.DELETE_REASON_LENGTH;

import com.lisovskyi.core_service.client.dto.request.ClientCreateRequest;
import com.lisovskyi.core_service.client.dto.request.ClientUpdateRequest;
import com.lisovskyi.core_service.client.dto.response.ClientResponse;
import com.sentio.shared.dto.PageResponse;
import com.sentio.shared.security.CurrentOrganizationId;
import com.sentio.shared.security.CurrentUserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/clients")
@RequiredArgsConstructor
@Validated
public class ClientController {

    private final ClientService clientService;

    @GetMapping
    public ResponseEntity<PageResponse<ClientResponse>> getAllClients(
            @CurrentOrganizationId Long organizationId,
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
            @CurrentOrganizationId Long organizationId, final Pageable pageable) {
        return ResponseEntity.ok(clientService.getAllDeletedClients(organizationId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClientResponse> getClientById(
            @PathVariable Long id, @CurrentOrganizationId Long organizationId) {
        return ResponseEntity.ok(clientService.getClientById(id, organizationId));
    }

    @PostMapping
    public ResponseEntity<ClientResponse> createClient(
            @CurrentOrganizationId Long organizationId,
            @CurrentUserId Long createdById,
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
            @CurrentOrganizationId Long organizationId,
            @CurrentUserId Long createdById,
            @RequestBody @Valid List<ClientCreateRequest> requests) {
        List<ClientResponse> response = clientService.createManyClients(organizationId, createdById, requests);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ClientResponse> updateClient(
            @PathVariable Long id,
            @CurrentOrganizationId Long organizationId,
            @RequestBody @Valid ClientUpdateRequest request) {
        return ResponseEntity.ok(clientService.updateClient(id, organizationId, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteClient(
            @PathVariable Long id,
            @CurrentOrganizationId Long organizationId,
            @CurrentUserId Long deletedById,
            @RequestParam(required = false) @Size(max = DELETE_REASON_LENGTH) String deleteReason) {
        clientService.deleteClient(id, organizationId, deletedById, deleteReason);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> restoreClient(
            @PathVariable Long id, @CurrentOrganizationId Long organizationId, @CurrentUserId Long restoredById) {
        clientService.restoreClient(id, organizationId, restoredById);
        return ResponseEntity.noContent().build();
    }
}

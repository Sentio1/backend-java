package com.lisovskyi.core_service.client;

import com.lisovskyi.core_service.client.dto.request.ClientCreateRequest;
import com.lisovskyi.core_service.client.dto.request.ClientSearchRequest;
import com.lisovskyi.core_service.client.dto.response.ClientResponse;
import com.sentio.shared.dto.PageResponse;
import com.sentio.shared.security.CurrentOrganizationId;
import com.sentio.shared.security.CurrentUserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.net.URI;

import static com.lisovskyi.core_service.client.ClientConstants.DELETE_REASON_LENGTH;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
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
            @CurrentOrganizationId Long organizationId, final Pageable pageable) {
        return ResponseEntity.ok(clientService.getAllClients(organizationId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClientResponse> getClientById(
            @PathVariable Long id, @CurrentOrganizationId Long organizationId) {
        return ResponseEntity.ok(clientService.getClientById(id, organizationId));
    }

    @GetMapping("/search")
    public ResponseEntity<PageResponse<ClientResponse>> searchClient(
            @CurrentOrganizationId Long organizationId,
            @Valid ClientSearchRequest request,
            final Pageable pageable) {
        return ResponseEntity.ok(clientService.searchClient(organizationId, request.query(), pageable));
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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteClient(
            @PathVariable Long id,
            @CurrentOrganizationId Long organizationId,
            @CurrentUserId Long deletedById,
            @RequestParam(required = false) @Size(max = DELETE_REASON_LENGTH) String deleteReason) {
        clientService.deleteClient(id, organizationId, deletedById, deleteReason);
        return ResponseEntity.noContent().build();
    }
}

package com.sentio.core_service.client.api.service;

import com.sentio.core_service.client.api.dto.ClientResponse;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ClientService {

    /** Throws ClientNotFoundException (404) when the client doesn't exist in this organization. */
    ClientResponse getByIdAndOrganizationId(long clientId, long organizationId);

    /** Clients of this organization by id; unknown/foreign ids are simply absent from the map. */
    Map<Long, ClientResponse> findAllByIdsAndOrganizationId(Collection<Long> clientIds, long organizationId);

    /** Name/company-name substring search, at most {@code limit} results. */
    List<ClientResponse> search(long organizationId, String query, int limit);

    Optional<ClientResponse> findByOrganizationIdAndRnokpp(long organizationId, String rnokpp);

    Optional<ClientResponse> findByOrganizationIdAndEdrpou(long organizationId, String edrpou);
}

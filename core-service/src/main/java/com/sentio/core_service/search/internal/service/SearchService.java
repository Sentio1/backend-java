package com.sentio.core_service.search.internal.service;

import static com.sentio.core_service.search.internal.SearchConstants.PAGE_SIZE;

import com.sentio.core_service.client.api.dto.ClientResponse;
import com.sentio.core_service.client.api.service.ClientService;
import com.sentio.core_service.client.api.validation.EdrpouValidator;
import com.sentio.core_service.client.api.validation.RnokppValidator;
import com.sentio.core_service.litigation.api.dto.CaseResponse;
import com.sentio.core_service.litigation.api.service.CaseService;
import com.sentio.core_service.search.internal.controller.dto.SearchResponse;
import com.sentio.shared.entity.id.organization.OrganizationId;
import io.micrometer.core.annotation.Timed;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Global search across modules - composes the client and litigation APIs, owns no data itself. */
@Service
@Slf4j
@RequiredArgsConstructor
public class SearchService {

    private final CaseService caseService;
    private final ClientService clientService;

    @Transactional(readOnly = true)
    @Timed(value = "core-service.search.duration", description = "Time taken for search")
    public SearchResponse search(@NonNull OrganizationId organizationId, String query) {
        if (!StringUtils.hasText(query)) {
            return buildEmptyResult();
        }

        boolean isEdrpou = EdrpouValidator.isValid(query);
        boolean isRnokpp = RnokppValidator.isValid(query);

        // Точний податковий ідентифікатор - шукаємо одного клієнта і всі його справи.
        if (isEdrpou || isRnokpp) {
            Optional<ClientResponse> client = isEdrpou
                    ? clientService.findByOrganizationIdAndEdrpou(organizationId.id(), query)
                    : clientService.findByOrganizationIdAndRnokpp(organizationId.id(), query);
            return client.map(found -> buildSearchResponse(found, organizationId)).orElseGet(this::buildEmptyResult);
        }

        List<ClientResponse> clients = clientService.search(organizationId.id(), query, PAGE_SIZE);
        List<CaseResponse> cases = caseService.searchByCaseNumber(organizationId.id(), query, PAGE_SIZE);
        return SearchResponse.builder()
                .clients(clients)
                .cases(cases)
                .build();
    }

    private SearchResponse buildSearchResponse(@NonNull ClientResponse client, @NonNull OrganizationId organizationId) {
        List<CaseResponse> cases = caseService.findAllByClient(client.id(), organizationId.id(), PAGE_SIZE);
        return SearchResponse.builder()
                .clients(List.of(client))
                .cases(cases)
                .build();
    }

    private SearchResponse buildEmptyResult() {
        return SearchResponse.builder().build();
    }
}

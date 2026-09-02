package com.lisovskyi.core_service.search;

import com.lisovskyi.core_service.case_.dto.response.CaseResponse;
import com.lisovskyi.core_service.case_.finder.CaseFinder;
import com.lisovskyi.core_service.case_.mapper.CaseMapper;
import com.lisovskyi.core_service.client.Client;
import com.lisovskyi.core_service.client.ClientRepository;
import com.lisovskyi.core_service.client.dto.response.ClientResponse;
import com.lisovskyi.core_service.client.finder.ClientFinder;
import com.lisovskyi.core_service.client.mapper.ClientMapper;
import com.lisovskyi.core_service.client.validation.EdrpouValidator;
import com.lisovskyi.core_service.client.validation.RnokppValidator;
import com.lisovskyi.core_service.search.dto.response.SearchResponse;
import com.sentio.shared.entity.id.organization.OrganizationId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.lisovskyi.core_service.search.SearchConstants.PAGE_SIZE;

@Service
@Slf4j
@RequiredArgsConstructor
public class SearchService {

    private final CaseFinder caseFinder;
    private final ClientFinder clientFinder;
    private final ClientRepository clientRepository;

    private final CaseMapper caseMapper;
    private final ClientMapper clientMapper;

    @Transactional(readOnly = true)
    public SearchResponse search(@NonNull OrganizationId organizationId, String query) {
        if (!StringUtils.hasText(query)) {
            return buildEmptyResult();
        }

        boolean isEdrpou = EdrpouValidator.isValid(query);
        boolean isRnokpp = RnokppValidator.isValid(query);

        Client client = null;
        List<CaseResponse> cases;

        if (isEdrpou || isRnokpp) {
            if (isEdrpou) {
                client = clientRepository.findByOrganizationIdAndEdrpou(organizationId.id(), query)
                        .orElse(null);
            } else {
                client = clientRepository.findByOrganizationIdAndRnokpp(organizationId.id(), query)
                        .orElse(null);
            }

            return buildSearchResponse(client, organizationId);
        } else {
            Page<Client> clients = clientFinder.searchClient(organizationId.id(), query, Pageable.ofSize(PAGE_SIZE));
            cases = caseFinder.findByOrganizationIdAndCaseNumberContainingIgnoreCase(organizationId.id(), query, Pageable.ofSize(PAGE_SIZE))
                    .stream()
                    .map(caseMapper::toResponse)
                    .toList();

            List<ClientResponse> clientResponses = clients.map(clientMapper::toResponse).toList();

            return SearchResponse.builder()
                    .clients(clientResponses)
                    .cases(cases)
                    .build();
        }
    }

    private SearchResponse buildSearchResponse(@Nullable Client client, @NonNull OrganizationId organizationId) {
        if (client == null) {
            return buildEmptyResult();
        }

        List<CaseResponse> cases = caseFinder.findAllByClientIdAndOrganizationId(client.getId(), organizationId.id(), Pageable.ofSize(PAGE_SIZE))
                .stream()
                .map(caseMapper::toResponse)
                .toList();

        ClientResponse clientResponse = clientMapper.toResponse(client);

        return SearchResponse.builder()
                .clients(clientResponse != null ? List.of(clientResponse) : List.of())
                .cases(cases)
                .build();
    }

    private SearchResponse buildEmptyResult() {
        return SearchResponse.builder().build();
    }
}

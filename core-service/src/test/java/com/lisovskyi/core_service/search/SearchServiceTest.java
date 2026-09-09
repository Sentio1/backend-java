package com.lisovskyi.core_service.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lisovskyi.core_service.case_.Case;
import com.lisovskyi.core_service.case_.dto.response.CaseResponse;
import com.lisovskyi.core_service.case_.finder.CaseFinder;
import com.lisovskyi.core_service.case_.mapper.CaseMapper;
import com.lisovskyi.core_service.client.Client;
import com.lisovskyi.core_service.client.ClientRepository;
import com.lisovskyi.core_service.client.dto.response.ClientResponse;
import com.lisovskyi.core_service.client.finder.ClientFinder;
import com.lisovskyi.core_service.client.mapper.ClientMapper;
import com.lisovskyi.core_service.search.dto.response.SearchResponse;
import com.sentio.shared.entity.id.organization.OrganizationId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
/** SearchServiceTest class. */
class SearchServiceTest {

    // валідні за контрольними алгоритмами (EdrpouValidatorTest/RnokppValidatorTest) значення
    private static final String VALID_EDRPOU = "12345678";
    private static final String VALID_RNOKPP = "1234567899";

    private static final OrganizationId ORG_ID = OrganizationId.of(1L);

    @Mock
    private CaseFinder caseFinder;

    @Mock
    private ClientFinder clientFinder;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private CaseMapper caseMapper;

    @Mock
    private ClientMapper clientMapper;

    @InjectMocks
    private SearchService searchService;

    private Client client(long id) {
        return Client.builder().id(id).organizationId(ORG_ID.id()).build();
    }

    private Case case_(long id) {
        return Case.builder().id(id).organizationId(ORG_ID.id()).title("Справа").build();
    }

    // ClientResponse/CaseResponse - плоскі record без @Builder, тож для тесту важливий лише id;
    // решту полів проставляємо null/false канонічним конструктором.
    private ClientResponse clientResponse(long id) {
        return new ClientResponse(
                id, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }

    private CaseResponse caseResponse(long id) {
        return new CaseResponse(
                id, null, null, null, null, null, null, null, null, null, null, null, null, false, null, null, null,
                null);
    }

    @Test
    void blankQuery_returnsEmptyResultWithoutHittingRepositories() {
        SearchResponse result = searchService.search(ORG_ID, "   ");

        assertThat(result.clients()).isNullOrEmpty();
        assertThat(result.cases()).isNullOrEmpty();
        verify(clientRepository, never()).findByOrganizationIdAndEdrpou(any(), any());
        verify(clientFinder, never()).searchClient(any(), any(), any());
    }

    @Test
    void validEdrpouWithMatch_returnsClientAndTheirCases() {
        Client client = client(10L);
        Case matchedCase = case_(20L);
        ClientResponse clientResponse = clientResponse(10L);
        CaseResponse caseResponse = caseResponse(20L);

        when(clientRepository.findByOrganizationIdAndEdrpou(ORG_ID.id(), VALID_EDRPOU))
                .thenReturn(Optional.of(client));
        when(caseFinder.findAllByClientIdAndOrganizationId(eq(10L), eq(ORG_ID.id()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(matchedCase)));
        when(clientMapper.toResponse(client)).thenReturn(clientResponse);
        when(caseMapper.toResponse(matchedCase)).thenReturn(caseResponse);

        SearchResponse result = searchService.search(ORG_ID, VALID_EDRPOU);

        assertThat(result.clients()).containsExactly(clientResponse);
        assertThat(result.cases()).containsExactly(caseResponse);
        verify(clientFinder, never()).searchClient(any(), any(), any());
    }

    @Test
    void validEdrpouWithNoMatch_returnsEmptyResult_notNotFound() {
        when(clientRepository.findByOrganizationIdAndEdrpou(ORG_ID.id(), VALID_EDRPOU))
                .thenReturn(Optional.empty());

        SearchResponse result = searchService.search(ORG_ID, VALID_EDRPOU);

        assertThat(result.clients()).isNullOrEmpty();
        assertThat(result.cases()).isNullOrEmpty();
        verify(caseFinder, never()).findAllByClientIdAndOrganizationId(any(), any(), any());
    }

    @Test
    void validRnokppWithMatch_returnsClientAndTheirCases() {
        Client client = client(11L);
        ClientResponse clientResponse = clientResponse(11L);

        when(clientRepository.findByOrganizationIdAndRnokpp(ORG_ID.id(), VALID_RNOKPP))
                .thenReturn(Optional.of(client));
        when(caseFinder.findAllByClientIdAndOrganizationId(eq(11L), eq(ORG_ID.id()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(clientMapper.toResponse(client)).thenReturn(clientResponse);

        SearchResponse result = searchService.search(ORG_ID, VALID_RNOKPP);

        assertThat(result.clients()).containsExactly(clientResponse);
        assertThat(result.cases()).isEmpty();
        verify(clientRepository, never()).findByOrganizationIdAndEdrpou(any(), any());
    }

    @Test
    void freeTextQuery_searchesBothClientsAndCasesByNumber() {
        Client client = client(30L);
        Case matchedCase = case_(40L);
        ClientResponse clientResponse = clientResponse(30L);
        CaseResponse caseResponse = caseResponse(40L);

        when(clientFinder.searchClient(eq(ORG_ID.id()), eq("Іваненко"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(client)));
        when(caseFinder.findByOrganizationIdAndCaseNumberContainingIgnoreCase(
                        eq(ORG_ID.id()), eq("Іваненко"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(matchedCase)));
        when(clientMapper.toResponse(client)).thenReturn(clientResponse);
        when(caseMapper.toResponse(matchedCase)).thenReturn(caseResponse);

        SearchResponse result = searchService.search(ORG_ID, "Іваненко");

        assertThat(result.clients()).containsExactly(clientResponse);
        assertThat(result.cases()).containsExactly(caseResponse);
        verify(clientRepository, never()).findByOrganizationIdAndEdrpou(any(), any());
        verify(clientRepository, never()).findByOrganizationIdAndRnokpp(any(), any());
    }

    @Test
    void freeTextQuery_noMatches_returnsEmptyListsNotError() {
        when(clientFinder.searchClient(eq(ORG_ID.id()), eq("nope"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(caseFinder.findByOrganizationIdAndCaseNumberContainingIgnoreCase(
                        eq(ORG_ID.id()), eq("nope"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        SearchResponse result = searchService.search(ORG_ID, "nope");

        assertThat(result.clients()).isEmpty();
        assertThat(result.cases()).isEmpty();
    }
}

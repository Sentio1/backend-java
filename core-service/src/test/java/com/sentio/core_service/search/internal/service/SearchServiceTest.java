package com.sentio.core_service.search.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sentio.core_service.client.api.dto.ClientResponse;
import com.sentio.core_service.client.api.service.ClientService;
import com.sentio.core_service.litigation.api.dto.CaseResponse;
import com.sentio.core_service.litigation.api.service.CaseService;
import com.sentio.core_service.search.internal.controller.dto.SearchResponse;
import com.sentio.shared.entity.id.organization.OrganizationId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    // валідні за контрольними алгоритмами (EdrpouValidatorTest/RnokppValidatorTest) значення
    private static final String VALID_EDRPOU = "12345678";
    private static final String VALID_RNOKPP = "1234567899";

    private static final OrganizationId ORG_ID = OrganizationId.of(1L);

    @Mock
    private CaseService caseService;

    @Mock
    private ClientService clientService;

    @InjectMocks
    private SearchService searchService;

    // ClientResponse/CaseResponse - плоскі record без @Builder, тож для тесту важливий лише id;
    // решту полів проставляємо null/false канонічним конструктором.
    private ClientResponse clientResponse(long id) {
        return new ClientResponse(
                id, 0L, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }

    private CaseResponse caseResponse(long id) {
        return new CaseResponse(
                id, 0L, 0L, null, null, null, null, null, null, null, null, null, null, false, null, null, null,
                null);
    }

    @Test
    void blankQuery_returnsEmptyResultWithoutQueryingAnything() {
        SearchResponse result = searchService.search(ORG_ID, "   ");

        assertThat(result.clients()).isNullOrEmpty();
        assertThat(result.cases()).isNullOrEmpty();
        verifyNoInteractions(clientService, caseService);
    }

    @Test
    void validEdrpouWithMatch_returnsClientAndTheirCases() {
        ClientResponse client = clientResponse(10L);
        CaseResponse matchedCase = caseResponse(20L);

        when(clientService.findByOrganizationIdAndEdrpou(ORG_ID.id(), VALID_EDRPOU)).thenReturn(Optional.of(client));
        when(caseService.findAllByClient(eq(10L), eq(ORG_ID.id()), anyInt())).thenReturn(List.of(matchedCase));

        SearchResponse result = searchService.search(ORG_ID, VALID_EDRPOU);

        assertThat(result.clients()).containsExactly(client);
        assertThat(result.cases()).containsExactly(matchedCase);
        verify(clientService, never()).search(anyLong(), anyString(), anyInt());
    }

    @Test
    void validEdrpouWithNoMatch_returnsEmptyResult_notNotFound() {
        when(clientService.findByOrganizationIdAndEdrpou(ORG_ID.id(), VALID_EDRPOU)).thenReturn(Optional.empty());

        SearchResponse result = searchService.search(ORG_ID, VALID_EDRPOU);

        assertThat(result.clients()).isNullOrEmpty();
        assertThat(result.cases()).isNullOrEmpty();
        verifyNoInteractions(caseService);
    }

    @Test
    void validRnokppWithMatch_returnsClientAndTheirCases() {
        ClientResponse client = clientResponse(11L);

        when(clientService.findByOrganizationIdAndRnokpp(ORG_ID.id(), VALID_RNOKPP)).thenReturn(Optional.of(client));
        when(caseService.findAllByClient(eq(11L), eq(ORG_ID.id()), anyInt())).thenReturn(List.of());

        SearchResponse result = searchService.search(ORG_ID, VALID_RNOKPP);

        assertThat(result.clients()).containsExactly(client);
        assertThat(result.cases()).isEmpty();
        verify(clientService, never()).findByOrganizationIdAndEdrpou(anyLong(), anyString());
    }

    @Test
    void freeTextQuery_searchesBothClientsAndCasesByNumber() {
        ClientResponse client = clientResponse(30L);
        CaseResponse matchedCase = caseResponse(40L);

        when(clientService.search(eq(ORG_ID.id()), eq("Іваненко"), anyInt())).thenReturn(List.of(client));
        when(caseService.searchByCaseNumber(eq(ORG_ID.id()), eq("Іваненко"), anyInt())).thenReturn(List.of(matchedCase));

        SearchResponse result = searchService.search(ORG_ID, "Іваненко");

        assertThat(result.clients()).containsExactly(client);
        assertThat(result.cases()).containsExactly(matchedCase);
        verify(clientService, never()).findByOrganizationIdAndEdrpou(anyLong(), anyString());
        verify(clientService, never()).findByOrganizationIdAndRnokpp(anyLong(), anyString());
    }

    @Test
    void freeTextQuery_noMatches_returnsEmptyListsNotError() {
        when(clientService.search(eq(ORG_ID.id()), eq("nope"), anyInt())).thenReturn(List.of());
        when(caseService.searchByCaseNumber(eq(ORG_ID.id()), eq("nope"), anyInt())).thenReturn(List.of());

        SearchResponse result = searchService.search(ORG_ID, "nope");

        assertThat(result.clients()).isEmpty();
        assertThat(result.cases()).isEmpty();
    }
}

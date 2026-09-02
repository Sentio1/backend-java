package com.lisovskyi.core_service.search.dto.response;

import com.lisovskyi.core_service.case_.dto.response.CaseResponse;
import com.lisovskyi.core_service.client.dto.response.ClientResponse;
import lombok.Builder;

import java.util.List;

@Builder
public record SearchResponse(List<ClientResponse> clients, List<CaseResponse> cases) {}

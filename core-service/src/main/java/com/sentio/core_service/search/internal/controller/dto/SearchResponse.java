package com.sentio.core_service.search.internal.controller.dto;

import com.sentio.core_service.litigation.api.dto.CaseResponse;
import com.sentio.core_service.client.api.dto.ClientResponse;
import lombok.Builder;

import java.util.List;

@Builder
public record SearchResponse(List<ClientResponse> clients, List<CaseResponse> cases) {}

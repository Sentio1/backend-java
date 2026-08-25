package com.lisovskyi.core_service.client.mapper;

import com.lisovskyi.core_service.client.Client;import com.lisovskyi.core_service.client.dto.request.ClientCreateRequest;import com.lisovskyi.core_service.client.dto.response.ClientResponse;import org.mapstruct.Mapper;import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ClientMapper {
    Client toEntity(ClientCreateRequest request);

    ClientResponse toResponse(Client client);
}

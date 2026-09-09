package com.lisovskyi.core_service.court.mapper;

import com.lisovskyi.core_service.court.Court;
import com.lisovskyi.core_service.court.dto.response.CourtResponse;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CourtMapper {

    @Mapping(target = "isActive", source = "active")
    CourtResponse toResponse(Court court);

    List<CourtResponse> toResponseList(List<Court> courts);
}

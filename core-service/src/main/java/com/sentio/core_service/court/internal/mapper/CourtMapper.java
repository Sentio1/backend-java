package com.sentio.core_service.court.internal.mapper;

import com.sentio.core_service.court.internal.model.Court;
import com.sentio.core_service.court.api.dto.CourtResponse;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface CourtMapper {

    @Mapping(target = "isActive", source = "active")
    CourtResponse toResponse(Court court);

    List<CourtResponse> toResponseList(List<Court> courts);
}

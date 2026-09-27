package com.sentio.core_service.court.api.service;

import com.sentio.core_service.court.api.dto.CourtResponse;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface CourtService {

    Optional<CourtResponse> findById(long courtId);

    /** Throws CourtNotFoundException (404) for an unknown id. */
    CourtResponse getById(long courtId);

    /** Courts by id; unknown ids are simply absent from the map. */
    Map<Long, CourtResponse> findAllByIds(Collection<Long> courtIds);
}

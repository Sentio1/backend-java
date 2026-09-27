package com.sentio.core_service.court.internal.service;

import com.sentio.core_service.court.api.dto.CourtResponse;
import com.sentio.core_service.court.api.service.CourtService;
import com.sentio.core_service.court.internal.exception.CourtNotFoundException;
import com.sentio.core_service.court.internal.mapper.CourtMapper;
import com.sentio.core_service.court.internal.repository.CourtRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourtServiceImpl implements CourtService {

    private final CourtRepository courtRepository;
    private final CourtMapper courtMapper;

    // Суди - глобальний довідник (без organization_id, спільний для всіх орендарів, див.
    // коментар на Court) і невеликий за обсягом, тому без пагінації/org-скоупу - весь список
    // одним запитом під UI-дропдаун вибору суду (CaseCreateRequest.courtId).
    @Transactional(readOnly = true)
    public List<CourtResponse> getAllCourts() {
        return courtMapper.toResponseList(courtRepository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CourtResponse> findById(long courtId) {
        return courtRepository.findById(courtId).map(courtMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public CourtResponse getById(long courtId) {
        return findById(courtId).orElseThrow(() -> new CourtNotFoundException(courtId));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, CourtResponse> findAllByIds(Collection<Long> courtIds) {
        if (courtIds.isEmpty()) {
            return Map.of();
        }
        return courtRepository.findAllById(courtIds).stream()
                .map(courtMapper::toResponse)
                .collect(Collectors.toMap(CourtResponse::id, Function.identity()));
    }
}

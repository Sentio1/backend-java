package com.lisovskyi.core_service.court.service;

import com.lisovskyi.core_service.court.CourtRepository;
import com.lisovskyi.core_service.court.dto.response.CourtResponse;
import com.lisovskyi.core_service.court.mapper.CourtMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourtService {

    private final CourtRepository courtRepository;
    private final CourtMapper courtMapper;

    // Суди - глобальний довідник (без organization_id, спільний для всіх орендарів, див.
    // коментар на Court) і невеликий за обсягом, тому без пагінації/org-скоупу - весь список
    // одним запитом під UI-дропдаун вибору суду (CaseCreateRequest.courtId).
    @Transactional(readOnly = true)
    public List<CourtResponse> getAllCourts() {
        return courtMapper.toResponseList(courtRepository.findAll());
    }
}

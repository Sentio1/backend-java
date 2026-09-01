package com.lisovskyi.core_service.court;

import com.lisovskyi.core_service.court.dto.response.CourtResponse;
import com.lisovskyi.core_service.court.service.CourtService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/courts")
@RequiredArgsConstructor
public class CourtController {

    private final CourtService courtService;

    // Поки один метод: повний список судів для UI-дропдауна вибору суду при
    // створенні/редагуванні справи (CaseCreateRequest/CaseUpdateRequest.courtId). Без
    // @CurrentOrganizationId - суди спільні для всіх організацій, не дані організації.
    @GetMapping
    public ResponseEntity<List<CourtResponse>> getAllCourts() {
        return ResponseEntity.ok(courtService.getAllCourts());
    }
}

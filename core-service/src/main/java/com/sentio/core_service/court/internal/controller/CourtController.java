package com.sentio.core_service.court.internal.controller;

import com.sentio.core_service.court.internal.service.CourtServiceImpl;

import com.sentio.core_service.court.api.dto.CourtResponse;

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

    private final CourtServiceImpl courtService;

    // Поки один метод: повний список судів для UI-дропдауна вибору суду при
    // створенні/редагуванні справи (CaseCreateRequest/CaseUpdateRequest.courtId). Без
    // @CurrentOrganizationId - суди спільні для всіх організацій, не дані організації.
    @GetMapping
    public ResponseEntity<List<CourtResponse>> getAllCourts() {
        return ResponseEntity.ok(courtService.getAllCourts());
    }
}

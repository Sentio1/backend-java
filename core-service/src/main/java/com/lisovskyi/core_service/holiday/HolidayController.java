package com.lisovskyi.core_service.holiday;

import com.lisovskyi.core_service.holiday.dto.request.HolidayCreateRequest;
import com.lisovskyi.core_service.holiday.dto.request.HolidayUpdateRequest;
import com.lisovskyi.core_service.holiday.dto.response.HolidayResponse;
import com.sentio.shared.dto.PageResponse;
import com.sentio.shared.web.LocationUtility;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/holidays")
@RequiredArgsConstructor
public class HolidayController {

    private final HolidayService holidayService;

    @GetMapping
    public ResponseEntity<PageResponse<HolidayResponse>> getAllHolidays(
            @RequestParam(required = false) Short year,
            final Pageable pageable
    ) {
        return ResponseEntity.ok(holidayService.getAllHolidays(year, pageable));
    }

    @GetMapping("/{date}")
    public ResponseEntity<HolidayResponse> getHolidayByDate(
            @PathVariable LocalDate date
    ) {
        return ResponseEntity.ok(holidayService.getHolidayByDate(date));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HolidayResponse> createHoliday(
            @RequestBody @Valid HolidayCreateRequest request
    ) {
        HolidayResponse holidayResponse = holidayService.createHoliday(request);
        return LocationUtility.createdWithLocation(holidayResponse.date(), holidayResponse);
    }

    @PatchMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HolidayResponse> updateHoliday(
            @RequestBody @Valid HolidayUpdateRequest request
    ) {
        return ResponseEntity.ok(holidayService.updateHoliday(request));
    }

    @DeleteMapping("/{date}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteHoliday(
            @PathVariable LocalDate date
    ) {
        holidayService.deleteHoliday(date);
        return ResponseEntity.noContent().build();
    }
}

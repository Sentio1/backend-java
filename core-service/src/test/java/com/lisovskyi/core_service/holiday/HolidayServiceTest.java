package com.lisovskyi.core_service.holiday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lisovskyi.core_service.holiday.dto.event.HolidayChangedEvent;
import com.lisovskyi.core_service.holiday.dto.request.HolidayCreateRequest;
import com.lisovskyi.core_service.holiday.dto.request.HolidayUpdateRequest;
import com.lisovskyi.core_service.holiday.dto.response.HolidayResponse;
import com.lisovskyi.core_service.holiday.enums.HolidayType;
import com.lisovskyi.core_service.holiday.finder.HolidayFinder;
import com.lisovskyi.core_service.holiday.mapper.HolidayMapper;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class HolidayServiceTest {

    @Mock
    private HolidayFinder holidayFinder;

    @Mock
    private HolidayRepository holidayRepository;

    @Mock
    private HolidayMapper holidayMapper;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private HolidayService holidayService;

    private Holiday holiday(LocalDate date, HolidayType type) {
        return Holiday.builder()
                .date(date)
                .name("Новий рік")
                .holidayType(type)
                .year((short) date.getYear())
                .effectiveFrom(date)
                .build();
    }

    private HolidayResponse response(LocalDate date) {
        return new HolidayResponse(date, "Новий рік", HolidayType.PUBLIC_HOLIDAY, false, (short) date.getYear(), date);
    }

    // ─── createHoliday ──────────────────────────────────────────────────

    @Test
    void createHoliday_setsYearFromDate_beforeSaving() {
        LocalDate date = LocalDate.of(2026, 1, 1);
        HolidayCreateRequest request = new HolidayCreateRequest(date, "Новий рік", HolidayType.PUBLIC_HOLIDAY, date);
        // year - навмисно НЕ виставлений мапером (мокований мапер повертає сутність без нього),
        // щоб тест ловив саме HolidayService.createHoliday, а не мапер, що ставить рік сам.
        Holiday mapped = Holiday.builder()
                .date(date)
                .name("Новий рік")
                .holidayType(HolidayType.PUBLIC_HOLIDAY)
                .effectiveFrom(date)
                .build();
        when(holidayMapper.toEntity(request)).thenReturn(mapped);
        when(holidayMapper.toResponse(mapped)).thenReturn(response(date));

        holidayService.createHoliday(request);

        assertThat(mapped.getYear()).isEqualTo((short) 2026);
        verify(holidayRepository).save(mapped);
    }

    @Test
    void createHoliday_publishesHolidayChangedEvent_withTheHolidaysDate() {
        LocalDate date = LocalDate.of(2026, 1, 1);
        HolidayCreateRequest request = new HolidayCreateRequest(date, "Новий рік", HolidayType.PUBLIC_HOLIDAY, date);
        Holiday mapped = holiday(date, HolidayType.PUBLIC_HOLIDAY);
        when(holidayMapper.toEntity(request)).thenReturn(mapped);
        when(holidayMapper.toResponse(mapped)).thenReturn(response(date));

        holidayService.createHoliday(request);

        ArgumentCaptor<HolidayChangedEvent> eventCaptor = ArgumentCaptor.forClass(HolidayChangedEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().date()).isEqualTo(date);
    }

    // ─── updateHoliday ──────────────────────────────────────────────────

    @Test
    void updateHoliday_notFound_throwsResourceNotFoundException_andNeverPublishes() {
        LocalDate date = LocalDate.of(2026, 1, 1);
        HolidayUpdateRequest request =
                new HolidayUpdateRequest(date, JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined());
        when(holidayRepository.findByDate(date)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> holidayService.updateHoliday(request)).isInstanceOf(ResourceNotFoundException.class);

        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    void updateHoliday_found_appliesPatchViaMapper_savesAndPublishes() {
        LocalDate date = LocalDate.of(2026, 1, 1);
        Holiday existing = holiday(date, HolidayType.PUBLIC_HOLIDAY);
        HolidayUpdateRequest request = new HolidayUpdateRequest(
                date, JsonNullable.undefined(), JsonNullable.of(HolidayType.TRANSFERRED_WORKING_DAY), JsonNullable.undefined());
        when(holidayRepository.findByDate(date)).thenReturn(Optional.of(existing));
        when(holidayRepository.save(existing)).thenReturn(existing);
        when(holidayMapper.toResponse(existing)).thenReturn(response(date));

        holidayService.updateHoliday(request);

        verify(holidayMapper).updateEntityFromRequest(request, existing);
        verify(holidayRepository).save(existing);
        verify(applicationEventPublisher, times(1)).publishEvent(new HolidayChangedEvent(date));
    }

    // ─── deleteHoliday ──────────────────────────────────────────────────

    @Test
    void deleteHoliday_deletesByDate_andPublishesHolidayChangedEvent() {
        LocalDate date = LocalDate.of(2026, 1, 1);

        holidayService.deleteHoliday(date);

        verify(holidayRepository).deleteByDate(date);
        verify(applicationEventPublisher).publishEvent(new HolidayChangedEvent(date));
    }

    // ─── getHolidayByDate ───────────────────────────────────────────────

    @Test
    void getHolidayByDate_delegatesToFinder_whichThrowsIfMissing() {
        LocalDate date = LocalDate.of(2026, 1, 1);
        Holiday found = holiday(date, HolidayType.PUBLIC_HOLIDAY);
        HolidayResponse expected = response(date);
        when(holidayFinder.findById(date)).thenReturn(found);
        when(holidayMapper.toResponse(found)).thenReturn(expected);

        assertThat(holidayService.getHolidayByDate(date)).isSameAs(expected);
    }

    // ─── getAllHolidays ─────────────────────────────────────────────────

    @Test
    void getAllHolidays_withYear_filtersByYear() {
        Pageable pageable = PageRequest.of(0, 20);
        Holiday found = holiday(LocalDate.of(2026, 1, 1), HolidayType.PUBLIC_HOLIDAY);
        Page<Holiday> page = new PageImpl<>(java.util.List.of(found));
        when(holidayRepository.findAllByYear((short) 2026, pageable)).thenReturn(page);
        when(holidayMapper.toResponse(found)).thenReturn(response(LocalDate.of(2026, 1, 1)));

        holidayService.getAllHolidays((short) 2026, pageable);

        verify(holidayFinder, never()).findAll(any(Pageable.class));
    }

    @Test
    void getAllHolidays_withoutYear_returnsUnfilteredPage() {
        Pageable pageable = PageRequest.of(0, 20);
        when(holidayFinder.findAll(pageable)).thenReturn(new PageImpl<>(java.util.List.of()));

        holidayService.getAllHolidays(null, pageable);

        verify(holidayRepository, never()).findAllByYear(anyShort(), any());
    }
}

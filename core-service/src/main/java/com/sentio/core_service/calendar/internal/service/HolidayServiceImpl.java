package com.sentio.core_service.calendar.internal.service;

import com.sentio.core_service.calendar.internal.model.Holiday;
import com.sentio.core_service.calendar.internal.repository.HolidayRepository;

import com.sentio.core_service.calendar.api.event.HolidayChangedEvent;
import com.sentio.core_service.calendar.internal.controller.dto.HolidayCreateRequest;
import com.sentio.core_service.calendar.internal.controller.dto.HolidayUpdateRequest;
import com.sentio.core_service.calendar.internal.controller.dto.HolidayResponse;
import com.sentio.core_service.calendar.internal.mapper.HolidayMapper;
import com.sentio.core_service.calendar.api.service.HolidayCalendar;
import com.sentio.core_service.calendar.internal.exception.HolidayNotFoundException;
import com.sentio.shared.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class HolidayServiceImpl implements HolidayCalendar {

    private final HolidayRepository holidayRepository;
    private final HolidayMapper holidayMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional(readOnly = true)
    public HolidayResponse getHolidayByDate(LocalDate date) {
        return holidayMapper.toResponse(holidayRepository.findById(date)
                .orElseThrow(() -> new HolidayNotFoundException(date)));
    }

    // year - опційний фільтр (та сама форма, що й DeadlineRuleService.getAllDeadlineRules
    // із ProcedureType): без нього - усі роки, тож пагінація тут не косметична, а необхідна -
    // календар накопичується з кожним роком сідингу.
    @Transactional(readOnly = true)
    public PageResponse<HolidayResponse> getAllHolidays(Short year, Pageable pageable) {
        return PageResponse.of(
                year != null
                        ? holidayRepository.findAllByYear(year, pageable).map(holidayMapper::toResponse)
                        : holidayRepository.findAll(pageable).map(holidayMapper::toResponse));
    }

    @Transactional
    public HolidayResponse createHoliday(HolidayCreateRequest request) {
        Holiday holiday = holidayMapper.toEntity(request);
        // year - похідне від date, а не з request (див. HolidayCreateRequest/HolidayMapper).
        holiday.setYear((short) holiday.getDate().getYear());
        holidayRepository.save(holiday);

        // Опублікувати подію, щоб інший сервіс міг перерахувати дедлайни
        applicationEventPublisher.publishEvent(new HolidayChangedEvent(holiday.getDate()));
        return holidayMapper.toResponse(holiday);
    }

    @Transactional
    public HolidayResponse updateHoliday(HolidayUpdateRequest request) {
        Holiday holiday = holidayRepository.findByDate(request.date())
                        .orElseThrow(() -> new HolidayNotFoundException(request.date()));
        holidayMapper.updateEntityFromRequest(request, holiday);

        applicationEventPublisher.publishEvent(new HolidayChangedEvent(holiday.getDate()));
        return holidayMapper.toResponse(holidayRepository.save(holiday));
    }

    @Transactional
    public void deleteHoliday(LocalDate date) {
        holidayRepository.deleteByDate(date);
        applicationEventPublisher.publishEvent(new HolidayChangedEvent(date));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<LocalDate, Boolean> findWorkingDayOverrides(LocalDate from, LocalDate to, LocalDate asOf) {
        return holidayRepository.findAllByDateBetween(from, to, asOf).stream()
                .collect(Collectors.toMap(Holiday::getDate, Holiday::isWorking));
    }
}

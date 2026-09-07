package com.lisovskyi.core_service.holiday;

import com.lisovskyi.core_service.holiday.dto.event.HolidayChangedEvent;
import com.lisovskyi.core_service.holiday.dto.request.HolidayCreateRequest;
import com.lisovskyi.core_service.holiday.dto.request.HolidayUpdateRequest;
import com.lisovskyi.core_service.holiday.dto.response.HolidayResponse;
import com.lisovskyi.core_service.holiday.finder.HolidayFinder;
import com.lisovskyi.core_service.holiday.mapper.HolidayMapper;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.shared.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@Slf4j
@RequiredArgsConstructor
public class HolidayService {

    private final HolidayFinder holidayFinder;
    private final HolidayRepository holidayRepository;
    private final HolidayMapper holidayMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional(readOnly = true)
    public HolidayResponse getHolidayByDate(LocalDate date) {
        return holidayMapper.toResponse(holidayFinder.findById(date));
    }

    // year - опційний фільтр (та сама форма, що й DeadlineRuleService.getAllDeadlineRules
    // із ProcedureType): без нього - усі роки, тож пагінація тут не косметична, а необхідна -
    // календар накопичується з кожним роком сідингу.
    @Transactional(readOnly = true)
    public PageResponse<HolidayResponse> getAllHolidays(Short year, Pageable pageable) {
        return PageResponse.of(
                year != null
                        ? holidayRepository.findAllByYear(year, pageable).map(holidayMapper::toResponse)
                        : holidayFinder.findAll(pageable).map(holidayMapper::toResponse));
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
                        .orElseThrow(() -> new ResourceNotFoundException("Holiday", "date", request.date()));
        holidayMapper.updateEntityFromRequest(request, holiday);

        applicationEventPublisher.publishEvent(new HolidayChangedEvent(holiday.getDate()));
        return holidayMapper.toResponse(holidayRepository.save(holiday));
    }

    @Transactional
    public void deleteHoliday(LocalDate date) {
        holidayRepository.deleteByDate(date);
        applicationEventPublisher.publishEvent(new HolidayChangedEvent(date));
    }
}

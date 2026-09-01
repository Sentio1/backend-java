package com.lisovskyi.core_service.holiday.finder;

import com.lisovskyi.core_service.holiday.Holiday;
import com.lisovskyi.core_service.holiday.HolidayRepository;
import com.sentio.shared.entity.finder.AbstractEntityFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class HolidayFinderImpl extends AbstractEntityFinder<Holiday, Long> implements HolidayFinder {

    private final HolidayRepository holidayRepository;

    @Override
    protected JpaRepository<Holiday, Long> getRepository() {
        return holidayRepository;
    }

    @Override
    protected String getEntityName() {
        return "Holiday";
    }

    @Override
    public List<Holiday> findAllByDateBetween(LocalDate from, LocalDate to) {
        requireNonNull(from, to);
        return holidayRepository.findAllByDateBetween(from, to);
    }
}

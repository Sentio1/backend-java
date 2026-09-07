package com.lisovskyi.core_service.holiday.finder;

import com.lisovskyi.core_service.holiday.Holiday;
import com.lisovskyi.core_service.holiday.HolidayRepository;
import com.sentio.shared.entity.finder.AbstractEntityFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class HolidayFinderImpl extends AbstractEntityFinder<Holiday, LocalDate> implements HolidayFinder {

    private final HolidayRepository holidayRepository;

    @Override
    protected JpaRepository<Holiday, LocalDate> getRepository() {
        return holidayRepository;
    }

    @Override
    protected String getEntityName() {
        return "Holiday";
    }

    @Override
    public List<Holiday> findAllByDateBetween(LocalDate from, LocalDate to, LocalDate asOf) {
        requireNonNull(from, to, asOf);
        return holidayRepository.findAllByDateBetween(from, to, asOf);
    }

    @Override
    public Map<LocalDate, Boolean> findWorkingDayOverrides(LocalDate from, LocalDate to, LocalDate asOf) {
        return findAllByDateBetween(from, to, asOf).stream()
                .collect(Collectors.toMap(Holiday::getDate, Holiday::isWorking));
    }
}

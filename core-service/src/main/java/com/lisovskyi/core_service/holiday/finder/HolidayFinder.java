package com.lisovskyi.core_service.holiday.finder;

import com.lisovskyi.core_service.holiday.Holiday;
import com.sentio.shared.entity.finder.EntityFinder;

import java.time.LocalDate;
import java.util.List;

public interface HolidayFinder extends EntityFinder<Holiday, Long> {

    List<Holiday> findAllByDateBetween(LocalDate from, LocalDate to);
}

package com.lisovskyi.core_service.holiday.finder;

import com.lisovskyi.core_service.holiday.Holiday;
import com.sentio.shared.entity.finder.EntityFinder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface HolidayFinder extends EntityFinder<Holiday, LocalDate> {

    // asOf: календар "як він відомий" на цю дату - рядки з effectiveFrom пізніше за asOf
    // (перенесення, оголошене, але ще не чинне) до вибірки не потрапляють.
    List<Holiday> findAllByDateBetween(LocalDate from, LocalDate to, LocalDate asOf);

    // Те саме, одразу матеріалізоване в Map<дата, чи робочий> - формат, який очікує
    // deadline_processor.DeadlineCalculator/holiday.WorkingDayCalendar.
    Map<LocalDate, Boolean> findWorkingDayOverrides(LocalDate from, LocalDate to, LocalDate asOf);
}

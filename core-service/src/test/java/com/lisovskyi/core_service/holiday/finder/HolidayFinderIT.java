package com.lisovskyi.core_service.holiday.finder;

import static org.assertj.core.api.Assertions.assertThat;

import com.lisovskyi.core_service.TestcontainersConfiguration;
import com.lisovskyi.core_service.holiday.Holiday;
import com.lisovskyi.core_service.holiday.HolidayRepository;
import com.lisovskyi.core_service.holiday.enums.HolidayType;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

// SEN-26: HolidayFinder на реальному core.holidays - і на сідингу 2026 року (V34), і на
// effectiveFrom-версійності (V33), яку WorkingDayCalendarTest/DeadlineCalculatorTest не можуть
// покрити, бо вони не торкаються БД взагалі.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class HolidayFinderIT {

    @Autowired
    private HolidayFinder holidayFinder;

    @Autowired
    private HolidayRepository holidayRepository;

    @Test
    void seed2026_newYearsDay_isNonWorking() {
        Map<LocalDate, Boolean> overrides = holidayFinder.findWorkingDayOverrides(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1));

        assertThat(overrides).containsEntry(LocalDate.of(2026, 1, 1), false);
    }

    @Test
    void seed2026_transferredMonday_afterASundayHoliday_isNonWorking() {
        // 08.03.2026 (8 березня) - неділя, тож 09.03.2026 (понеділок) - перенесений вихідний.
        Map<LocalDate, Boolean> overrides = holidayFinder.findWorkingDayOverrides(
                LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 9));

        assertThat(overrides).containsEntry(LocalDate.of(2026, 3, 9), false);
    }

    @Test
    void findWorkingDayOverrides_dateNotInTable_isAbsentFromTheMap() {
        // 31.12.2026 (четвер) не засіяний - календар про нього нічого не каже, і
        // WorkingDayCalendar трактує його як звичайний будній день за DayOfWeek.
        LocalDate dec31 = LocalDate.of(2026, 12, 31);

        Map<LocalDate, Boolean> overrides = holidayFinder.findWorkingDayOverrides(dec31, dec31, dec31);

        assertThat(overrides).doesNotContainKey(dec31);
    }

    @Test
    void findWorkingDayOverrides_rowAnnouncedInTheFuture_isExcluded_untilAsOfReachesEffectiveFrom() {
        // Перенесена робоча субота, "оголошена" 01.10.2026 (effectiveFrom) - імітує КМУ-подібне
        // рішення посеред року про конкретну дату в майбутньому.
        LocalDate transferredSaturday = LocalDate.of(2026, 11, 14);
        LocalDate announcedOn = LocalDate.of(2026, 10, 1);
        holidayRepository.save(Holiday.builder()
                .date(transferredSaturday)
                .name("Відпрацювання за перенесенням")
                .holidayType(HolidayType.TRANSFERRED_WORKING_DAY)
                .year((short) 2026)
                .effectiveFrom(announcedOn)
                .build());

        Map<LocalDate, Boolean> beforeAnnouncement = holidayFinder.findWorkingDayOverrides(
                transferredSaturday, transferredSaturday, announcedOn.minusDays(1));
        Map<LocalDate, Boolean> afterAnnouncement = holidayFinder.findWorkingDayOverrides(
                transferredSaturday, transferredSaturday, announcedOn);

        assertThat(beforeAnnouncement).doesNotContainKey(transferredSaturday);
        assertThat(afterAnnouncement).containsEntry(transferredSaturday, true);
    }
}

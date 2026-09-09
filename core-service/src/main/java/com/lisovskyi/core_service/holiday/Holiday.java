package com.lisovskyi.core_service.holiday;

import static com.lisovskyi.core_service.holiday.HolidayConstants.NAME_LENGTH;

import com.lisovskyi.core_service.holiday.enums.HolidayType;
import jakarta.persistence.*;

import java.time.LocalDate;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// Спільний виробничий календар для всіх орендарів — БЕЗ organization_id.
// На відміну від решти сутностей схеми core, первинний ключ тут природний (дата),
// а не сурогатний id через послідовність, тому клас не успадковує BaseEntity.
@Entity
@Table(name = "holidays")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Holiday {

    @Id
    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "name", nullable = false, length = NAME_LENGTH)
    private String name;

    // Єдине джерело правди про природу дня. isWorking() нижче — похідний метод, а не
    // окрема колонка: два незалежних поля з тим самим фактом ("чи робочий") могли б
    // розійтись (is_working каже одне, holidayType — інше); лишень одна збережена
    // властивість — розходитись нема з чим, і це справедливо навіть для щойно
    // побудованого через Holiday.builder() і ще не збереженого об'єкта (на відміну від
    // синхронізації через @PrePersist/@PreUpdate, яка спрацювала б лише при флаші).
    @Enumerated(EnumType.STRING)
    @Column(name = "holiday_type", nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private HolidayType holidayType;

    @Column(name = "year", nullable = false)
    private short year;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    // Похідне від holidayType, не окрема колонка — WorkingDayCalendar/DeadlineCalculator і
    // HolidayFinderImpl.findWorkingDayOverrides читають саме цей метод.
    public boolean isWorking() {
        return holidayType == HolidayType.TRANSFERRED_WORKING_DAY;
    }
}

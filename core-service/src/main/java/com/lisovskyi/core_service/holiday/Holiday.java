package com.lisovskyi.core_service.holiday;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDate;

import static com.lisovskyi.core_service.holiday.HolidayConstants.NAME_LENGTH;

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
    @Column(name = "date")
    private LocalDate date;

    @Column(name = "name", nullable = false, length = NAME_LENGTH)
    private String name;

    // перенесені робочі суботи
    @Column(name = "is_working", nullable = false)
    @Builder.Default
    private boolean isWorking = false;
}

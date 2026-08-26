package com.lisovskyi.core_service.holiday;

import static com.lisovskyi.core_service.holiday.HolidayConstants.NAME_LENGTH;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    private Boolean isWorking = false;
}

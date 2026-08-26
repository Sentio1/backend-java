package com.lisovskyi.core_service.court;

import static com.lisovskyi.core_service.court.CourtConstants.CODE_LENGTH;
import static com.lisovskyi.core_service.court.CourtConstants.NAME_LENGTH;
import static com.lisovskyi.core_service.court.CourtConstants.REGION_LENGTH;

import com.lisovskyi.jpa.autoconfigure.entity.BaseEntity;
import com.lisovskyi.jpa.autoconfigure.generator.SequenceSize;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

// Глобальний довідник судів — БЕЗ organization_id: суди спільні для всіх орендарів,
// це не дані організації, тому без TimestampedEntity/софт-делету.
@Entity
@Table(name = "courts")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@SequenceSize(size = 50)
public class Court extends BaseEntity {

    @Column(name = "name", nullable = false, length = NAME_LENGTH)
    private String name;

    // код суду з номера справи: 761, 522
    @Column(name = "code", unique = true, length = CODE_LENGTH)
    private String code;

    // 1 / 2 / 3
    @Column(name = "instance", nullable = false)
    private Short instance;

    @Column(name = "region", length = REGION_LENGTH)
    private String region;

    @Column(name = "address", columnDefinition = "text")
    private String address;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}

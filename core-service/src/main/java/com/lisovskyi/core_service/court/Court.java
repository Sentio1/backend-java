package com.lisovskyi.core_service.court;

import static com.lisovskyi.core_service.court.CourtConstants.*;

import com.lisovskyi.jpa.autoconfigure.entity.BaseEntity;
import com.lisovskyi.jpa.autoconfigure.generator.SequenceSize;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
    @Enumerated(EnumType.STRING)
    @Column(name = "instance", nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private CourtInstance courtInstance;

    @Column(name = "region", length = REGION_LENGTH)
    private String region;

    // IANA zone id (напр. "Europe/Kyiv") - джерело істини для конвертації occurred_at ->
    // календарна дата в Deadline Engine. Свідомо тут, а не на Organization: суд - локальна
    // сутність цього ж сервісу (без мережі до user-service), і зона фізично належить суду,
    // а не юрфірмі, яка через нього судиться.
    @Column(name = "time_zone", nullable = false, length = TIME_ZONE_LENGTH)
    @Builder.Default
    private String timeZone = "Europe/Kyiv";

    @Column(name = "address", columnDefinition = "text")
    private String address;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;
}

package com.sentio.core_service.deadline.internal.model;

import static com.sentio.core_service.deadline.internal.DeadlineRuleConstants.*;

import com.sentio.core_service.litigation.api.enums.ProcedureType;
import com.sentio.core_service.litigation.api.enums.EventCode;
import com.sentio.core_service.court.api.enums.CourtInstance;
import com.sentio.core_service.deadline.internal.enums.CountFrom;
import com.sentio.core_service.deadline.internal.enums.DayKind;
import com.sentio.core_service.deadline.internal.enums.DurationUnit;
import com.lisovskyi.jpa.autoconfigure.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// Довідник правил, версіонований — редакції кодексів змінюються, і старі справи не
// мають "переїжджати" на нові строки. БЕЗ organization_id: правила спільні для всіх
// орендарів, і без софт-делету/таймстемпів — версійність тут через valid_from/valid_to.
@Entity
@Table(name = "deadline_rules")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class DeadlineRule extends BaseEntity {

    // 'CPC_STATEMENT_OF_DEFENCE'
    @Column(name = "code", nullable = false, length = CODE_LENGTH)
    private String code;

    @Column(name = "procedure", nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ProcedureType procedure;

    @Column(name = "instance", nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private CourtInstance courtInstance;

    // яка подія запускає відлік
    @Column(name = "trigger_event_code", nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private EventCode triggerEventCode;

    @Column(name = "title", nullable = false, length = TITLE_LENGTH)
    private String title;

    // 'ст. 178 ЦПК України'
    @Column(name = "legal_basis", nullable = false, length = LEGAL_BASIS_LENGTH)
    private String legalBasis;

    @Column(name = "duration_value", nullable = false)
    private short durationValue;

    // DAY | MONTH
    @Column(name = "duration_unit", nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default
    private DurationUnit durationUnit = DurationUnit.DAY;

    @Column(name = "day_kind", nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default
    private DayKind dayKind = DayKind.CALENDAR;

    // строк тече з наступного дня
    @Column(name = "count_from", nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default
    private CountFrom countFrom = CountFrom.NEXT_DAY;

    @Column(name = "is_extendable", nullable = false)
    @Builder.Default
    private boolean isExtendable = false;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    // null = чинне
    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "version", nullable = false)
    @Builder.Default
    private short version = 1;
}

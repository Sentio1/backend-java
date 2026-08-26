package com.lisovskyi.core_service.deadline_rule;

import static com.lisovskyi.core_service.deadline_rule.DeadlineRuleConstants.CODE_LENGTH;
import static com.lisovskyi.core_service.deadline_rule.DeadlineRuleConstants.COUNT_FROM_LENGTH;
import static com.lisovskyi.core_service.deadline_rule.DeadlineRuleConstants.DURATION_UNIT_LENGTH;
import static com.lisovskyi.core_service.deadline_rule.DeadlineRuleConstants.LEGAL_BASIS_LENGTH;
import static com.lisovskyi.core_service.deadline_rule.DeadlineRuleConstants.TITLE_LENGTH;
import static com.lisovskyi.core_service.deadline_rule.DeadlineRuleConstants.TRIGGER_EVENT_CODE_LENGTH;

import com.lisovskyi.core_service.case_.ProcedureType;
import com.lisovskyi.jpa.autoconfigure.entity.BaseEntity;
import com.lisovskyi.jpa.autoconfigure.generator.SequenceSize;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
@SequenceSize(size = 50)
public class DeadlineRule extends BaseEntity {

    // 'CPC_STATEMENT_OF_DEFENCE'
    @Column(name = "code", nullable = false, length = CODE_LENGTH)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "procedure", nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ProcedureType procedure;

    // яка подія запускає відлік
    @Column(name = "trigger_event_code", nullable = false, length = TRIGGER_EVENT_CODE_LENGTH)
    private String triggerEventCode;

    @Column(name = "title", nullable = false, length = TITLE_LENGTH)
    private String title;

    // 'ст. 178 ЦПК України'
    @Column(name = "legal_basis", nullable = false, length = LEGAL_BASIS_LENGTH)
    private String legalBasis;

    @Column(name = "duration_value", nullable = false)
    private Short durationValue;

    // DAY | MONTH
    @Column(name = "duration_unit", nullable = false, length = DURATION_UNIT_LENGTH)
    @Builder.Default
    private String durationUnit = "DAY";

    @Enumerated(EnumType.STRING)
    @Column(name = "day_kind", nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default
    private DayKind dayKind = DayKind.CALENDAR;

    // строк тече з наступного дня
    @Column(name = "count_from", nullable = false, length = COUNT_FROM_LENGTH)
    @Builder.Default
    private String countFrom = "NEXT_DAY";

    @Column(name = "is_extendable", nullable = false)
    @Builder.Default
    private Boolean isExtendable = false;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    // null = чинне
    @Column(name = "valid_to")
    private LocalDate validTo;
}

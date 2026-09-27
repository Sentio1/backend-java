package com.sentio.core_service.deadline.internal.model;

import com.sentio.core_service.deadline.internal.service.DeadlineService;

import static com.sentio.core_service.deadline.internal.DeadlineConstants.*;

import com.sentio.core_service.deadline.internal.enums.DeadlineSource;
import com.sentio.core_service.deadline.internal.enums.DeadlineStatus;
import com.sentio.core_service.deadline.internal.enums.DayKind;
import com.sentio.core_service.deadline.internal.enums.DurationUnit;
import com.sentio.core_service.common.model.CoreEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

// Ядро продукту — розраховані строки по справі.
@Entity
@Table(name = "deadlines")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@SQLRestriction("deleted_at IS NULL")
public class Deadline extends CoreEntity {

    @Column(name = "organization_id", nullable = false)
    private long organizationId;

    // Case and CaseEvent belong to the litigation module - referenced by id only (the FKs to
    // core.cases / core.case_events stay in the DB). Read them through litigation's api.
    @Column(name = "case_id", nullable = false)
    private long caseId;

    // фізичний FK у межах core-service: core.deadline_rules(id)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", referencedColumnName = "id")
    private DeadlineRule rule;

    // фізичний FK у межах core-service: core.case_events(id); null для ручного дедлайна без події.
    @Column(name = "triggering_event_id")
    private Long triggeringEventId;

    @Column(name = "title", nullable = false, length = TITLE_LENGTH)
    private String title;

    // знімок на момент розрахунку — зміна кодексу не переписує історію
    @Column(name = "legal_basis", length = LEGAL_BASIS_LENGTH)
    private String legalBasis;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "source", nullable = false)
    @Builder.Default
    private DeadlineSource source = DeadlineSource.RULE;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    // порахована дата
    @Column(name = "due_on", nullable = false)
    private LocalDate dueOn;

    // "до перенесення" - те саме, що dueOn, якщо календар нічого не переніс (SEN-28).
    // Nullable з тієї ж причини, що й baseDate: для рядків, порахованих до SEN-28, значення
    // невідоме і заднім числом не відновлюється.
    @Column(name = "naive_due_on")
    private LocalDate naiveDueOn;

    // Знімок правила на момент розрахунку (SEN-28) - разом з legalBasis/title вище. Читати ці
    // три значення напряму з rule небезпечно: DeadlineRuleService.updateDeadlineRule редагує
    // рядок DeadlineRule на місці (без нової версії), тож "жива" durationValue могла піти в
    // інший бік від тієї, що реально застосовувалась тут.
    @Column(name = "duration_value")
    private Short durationValue;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "duration_unit")
    private DurationUnit durationUnit;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "day_kind")
    private DayKind dayKind;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private DeadlineStatus status = DeadlineStatus.PENDING;

    @Column(name = "rule_version")
    private Short ruleVersion;

    @Column(name = "needs_checking", nullable = false)
    @Builder.Default
    private boolean needsChecking = false;

    @Column(name = "completed_at")
    private Instant completedAt;

    // soft-ref auth.users.id
    @Column(name = "completed_by")
    private Long completedBy;

    @Column(name = "extended_to")
    private LocalDate extendedTo;

    @Column(name = "base_date")
    private LocalDate baseDate;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    // Заповнені лише коли status = REJECTED (SEN-29 AC4) - юрист вирішив, що автоматично
    // порахований строк не застосовний, і лишив причину. triggeringEventId і сама подія при
    // цьому нікуди не діваються, тож "чому нема дедлайна" завжди можна відновити з цих полів,
    // а не лише мовчазним null-ом deadlineId в CaseEventResponse.
    @Column(name = "rejected_at")
    private Instant rejectedAt;

    // soft-ref auth.users.id
    @Column(name = "rejected_by")
    private Long rejectedBy;

    @Column(name = "rejection_reason", length = REJECTION_REASON_LENGTH)
    private String rejectionReason;

    // soft-ref auth.users.id. Заповнене лише для source = MANUAL (DeadlineService.createManualDeadline) -
    // RULE-дедлайни не мають "автора" в цьому сенсі, їх ніколи не "створює" людина безпосередньо,
    // лише подія/DeadlineEngine, тож лишається null (той самий принцип, що й ruleVersion == null
    // для MANUAL: поле застосовне лише для одного з двох source).
    @Column(name = "created_by")
    private Long createdBy;
}

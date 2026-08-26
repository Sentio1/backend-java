package com.lisovskyi.core_service.deadline;

import static com.lisovskyi.core_service.deadline.DeadlineConstants.LEGAL_BASIS_LENGTH;
import static com.lisovskyi.core_service.deadline.DeadlineConstants.TITLE_LENGTH;

import com.lisovskyi.core_service.entity.CoreEntity;
import com.lisovskyi.jpa.autoconfigure.generator.SequenceSize;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
@SequenceSize(size = 50)
@SQLRestriction("deleted_at IS NULL")
public class Deadline extends CoreEntity {

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    // фізичний FK у межах core-service: core.cases(id)
    @Column(name = "case_id", nullable = false)
    private Long caseId;

    // фізичний FK у межах core-service: core.deadline_rules(id)
    @Column(name = "rule_id")
    private Long ruleId;

    // фізичний FK у межах core-service: core.case_events(id)
    @Column(name = "triggering_event_id")
    private Long triggeringEventId;

    @Column(name = "title", nullable = false, length = TITLE_LENGTH)
    private String title;

    // знімок на момент розрахунку — зміна кодексу не переписує історію
    @Column(name = "legal_basis", length = LEGAL_BASIS_LENGTH)
    private String legalBasis;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    // порахована дата
    @Column(name = "due_on", nullable = false)
    private LocalDate dueOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default
    private DeadlineStatus status = DeadlineStatus.PENDING;

    @Column(name = "completed_at")
    private Instant completedAt;

    // soft-ref auth.users.id
    @Column(name = "completed_by")
    private Long completedBy;

    @Column(name = "extended_to")
    private LocalDate extendedTo;

    @Column(name = "note", columnDefinition = "text")
    private String note;
}

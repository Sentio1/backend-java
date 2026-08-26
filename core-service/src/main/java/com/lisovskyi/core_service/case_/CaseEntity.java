package com.lisovskyi.core_service.case_;

import static com.lisovskyi.core_service.case_.CaseConstants.CASE_NUMBER_LENGTH;
import static com.lisovskyi.core_service.case_.CaseConstants.INTERNAL_NUMBER_LENGTH;
import static com.lisovskyi.core_service.case_.CaseConstants.JUDGE_NAME_LENGTH;
import static com.lisovskyi.core_service.case_.CaseConstants.TITLE_LENGTH;

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

// Таблиця core.cases. Клас названо CaseEntity, а не Case — "case" зарезервоване слово
// Java і не може бути ідентифікатором; послідовність відповідно перейменована на
// core.case_entity_seq_gen у V4__cases.sql (детальніше — коментар там).
@Entity
@Table(name = "cases")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@SequenceSize(size = 50)
@SQLRestriction("deleted_at IS NULL")
public class CaseEntity extends CoreEntity {

    // soft-ref auth.organizations.id — окрема БД, тому plain bigint, не @ManyToOne
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    // soft-ref auth.users.id
    @Column(name = "responsible_user_id", nullable = false)
    private Long responsibleUserId;

    // 761/4823/25 — null поки не відкрито провадження
    @Column(name = "case_number", length = CASE_NUMBER_LENGTH)
    private String caseNumber;

    // власна нумерація бюро
    @Column(name = "internal_number", length = INTERNAL_NUMBER_LENGTH)
    private String internalNumber;

    @Column(name = "title", nullable = false, length = TITLE_LENGTH)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "procedure", nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ProcedureType procedure;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default
    private CaseStatus status = CaseStatus.DRAFT;

    // фізичний FK у межах core-service: core.courts(id)
    @Column(name = "court_id")
    private Long courtId;

    @Column(name = "judge_name", length = JUDGE_NAME_LENGTH)
    private String judgeName;

    @Column(name = "opened_at")
    private LocalDate openedAt;

    @Column(name = "closed_at")
    private LocalDate closedAt;

    @Column(name = "registry_watch_enabled", nullable = false)
    @Builder.Default
    private Boolean registryWatchEnabled = false;

    @Column(name = "registry_last_checked_at")
    private Instant registryLastCheckedAt;
}

package com.lisovskyi.core_service.case_;

import static com.lisovskyi.core_service.case_.CaseConstants.*;

import com.lisovskyi.core_service.case_.enums.CaseStatus;
import com.lisovskyi.core_service.case_.enums.ProcedureType;
import com.lisovskyi.core_service.court.Court;
import com.lisovskyi.core_service.entity.CoreEntity;
import com.lisovskyi.jpa.autoconfigure.generator.SequenceSize;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
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
public class Case extends CoreEntity {

    // soft-ref auth.organizations.id — окрема БД, тому plain bigint, не @ManyToOne
    @Column(name = "organization_id", nullable = false)
    private long organizationId;

    // soft-ref auth.users.id
    @Column(name = "responsible_user_id", nullable = false)
    private long responsibleUserId;

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
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "court_id", referencedColumnName = "id")
    private Court court;

    @Column(name = "judge_name", length = JUDGE_NAME_LENGTH)
    private String judgeName;

    @Column(name = "opened_at")
    private LocalDate openedAt;

    @Column(name = "closed_at")
    private LocalDate closedAt;

    @Column(name = "registry_watch_enabled", nullable = false)
    @Builder.Default
    private boolean registryWatchEnabled = false;

    @Column(name = "registry_last_checked_at")
    private Instant registryLastCheckedAt;
}

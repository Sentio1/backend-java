package com.sentio.core_service.litigation.internal.model;

import static com.sentio.core_service.litigation.internal.CaseConstants.*;

import com.sentio.core_service.litigation.api.enums.CaseInstance;
import com.sentio.core_service.litigation.api.enums.CaseStatus;
import com.sentio.core_service.litigation.api.enums.ProcedureType;
import com.sentio.core_service.common.model.CoreEntity;
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
public class Case extends CoreEntity {

    // soft-ref auth.organizations.id — окрема БД, тому plain bigint, не @ManyToOne
    @Column(name = "organization_id", nullable = false)
    private long organizationId;

    // soft-ref auth.users.id
    @Column(name = "responsible_user_id", nullable = false)
    private long responsibleUserId;

    // soft-ref auth.users.id - хто завів справу (не обов'язково той самий, що responsibleUserId)
    @Column(name = "created_by", nullable = false)
    private long createdBy;

    // 761/4823/25 — null поки не відкрито провадження
    @Column(name = "case_number", length = CASE_NUMBER_LENGTH)
    private String caseNumber;

    // власна нумерація бюро
    @Column(name = "internal_number", length = INTERNAL_NUMBER_LENGTH)
    private String internalNumber;

    @Column(name = "title", nullable = false, length = TITLE_LENGTH)
    private String title;

    @Column(name = "procedure", nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default
    private ProcedureType procedure = ProcedureType.OTHER;

    @Column(name = "instance", nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private CaseInstance instance;

    @Column(name = "status", nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default
    private CaseStatus status = CaseStatus.ACTIVE;

    // фізичний FK у межах core-service: core.courts(id)
    // Court belongs to the court module - referenced by id only (the FK to core.courts stays in
    // the DB). Resolve it through court's CourtService when a response needs the full court.
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
    private boolean registryWatchEnabled = false;

    @Column(name = "registry_last_checked_at")
    private Instant registryLastCheckedAt;
}

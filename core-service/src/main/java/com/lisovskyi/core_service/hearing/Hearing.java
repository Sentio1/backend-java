package com.lisovskyi.core_service.hearing;

import static com.lisovskyi.core_service.hearing.HearingConstants.COURTROOM_LENGTH;
import static com.lisovskyi.core_service.hearing.HearingConstants.KIND_LENGTH;

import com.lisovskyi.core_service.case_.Case;
import com.lisovskyi.core_service.entity.CoreEntity;
import com.lisovskyi.jpa.autoconfigure.generator.SequenceSize;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "hearings")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@SequenceSize(size = 50)
public class Hearing extends CoreEntity {

    @Column(name = "organization_id", nullable = false)
    private long organizationId;

    // фізичний FK у межах core-service: core.cases(id)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false, referencedColumnName = "id")
    private Case case_;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    // підготовче / по суті / апеляційне
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = KIND_LENGTH)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private HearingKind kind;

    @Column(name = "courtroom", length = COURTROOM_LENGTH)
    private String courtroom;

    // заповнюється після засідання -> йде у звіт клієнту
    @Column(name = "outcome", columnDefinition = "text")
    private String outcome;

    @Column(name = "reported_to_client_at")
    private Instant reportedToClientAt;
}

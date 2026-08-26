package com.lisovskyi.core_service.hearing;

import static com.lisovskyi.core_service.hearing.HearingConstants.COURTROOM_LENGTH;
import static com.lisovskyi.core_service.hearing.HearingConstants.KIND_LENGTH;

import com.lisovskyi.core_service.entity.CoreEntity;
import com.lisovskyi.jpa.autoconfigure.generator.SequenceSize;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "hearings")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@SequenceSize(size = 50)
@SQLRestriction("deleted_at IS NULL")
public class Hearing extends CoreEntity {

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    // фізичний FK у межах core-service: core.cases(id)
    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    // підготовче / по суті / апеляційне
    @Column(name = "kind", length = KIND_LENGTH)
    private String kind;

    @Column(name = "courtroom", length = COURTROOM_LENGTH)
    private String courtroom;

    // заповнюється після засідання -> йде у звіт клієнту
    @Column(name = "outcome", columnDefinition = "text")
    private String outcome;

    @Column(name = "reported_to_client_at")
    private Instant reportedToClientAt;
}

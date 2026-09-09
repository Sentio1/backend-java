package com.lisovskyi.core_service.case_event_occurred_at_history;

import com.lisovskyi.core_service.case_event.CaseEvent;
import com.lisovskyi.core_service.entity.CoreEntityCreatedOnly;
import com.lisovskyi.jpa.autoconfigure.generator.SequenceSize;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "case_event_occurred_at_histories")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@SequenceSize(size = 50)
public class CaseEventOccurredAtHistory extends CoreEntityCreatedOnly {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_event_id", nullable = false, referencedColumnName = "id")
    private CaseEvent caseEvent;

    // soft-ref: auth.organizations.id
    @Column(name = "organization_id", nullable = false)
    private long organizationId;

    @Column(name = "old_occurred_at", nullable = false)
    private Instant oldOccurredAt;

    @Column(name = "new_occurred_at", nullable = false)
    private Instant newOccurredAt;

    // soft-ref: auth.users.id
    @Column(name = "changed_by", nullable = false)
    private long changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "reason", nullable = false, length = 255)
    private String reason;
}

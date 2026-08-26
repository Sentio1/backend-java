package com.lisovskyi.core_service.case_event;

import static com.lisovskyi.core_service.case_event.CaseEventConstants.EVENT_CODE_LENGTH;
import static com.lisovskyi.core_service.case_event.CaseEventConstants.SOURCE_LENGTH;
import static com.lisovskyi.core_service.case_event.CaseEventConstants.TITLE_LENGTH;

import com.lisovskyi.core_service.entity.CoreEntityCreatedOnly;
import com.lisovskyi.jpa.autoconfigure.generator.SequenceSize;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

// Хронологія справи — фіксує події, що запускають відлік строків.
// Таблиця не має updated_at, тому CreationTimestampedEntity (лише createdAt), не
// TimestampedEntity.
@Entity
@Table(name = "case_events")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@SequenceSize(size = 50)
@SQLRestriction("deleted_at IS NULL")
public class CaseEvent extends CoreEntityCreatedOnly {

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    // фізичний FK у межах core-service: core.cases(id)
    @Column(name = "case_id", nullable = false)
    private Long caseId;

    // 'CLAIM_FILED', 'RULING_RECEIVED'
    @Column(name = "event_code", nullable = false, length = EVENT_CODE_LENGTH)
    private String eventCode;

    @Column(name = "title", nullable = false, length = TITLE_LENGTH)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    // коли подія фактично сталася — від неї рахується строк
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    // коли зафіксована/отримана — може відрізнятись від occurredAt
    @Column(name = "registered_at")
    private Instant registeredAt;

    // MANUAL | REGISTRY
    @Column(name = "source", nullable = false, length = SOURCE_LENGTH)
    @Builder.Default
    private String source = "MANUAL";

    // soft-ref на документ у Registry Monitor (окремий сервіс, не FK)
    @Column(name = "registry_document_id")
    private Long registryDocumentId;

    // soft-ref auth.users.id
    @Column(name = "created_by")
    private Long createdBy;
}

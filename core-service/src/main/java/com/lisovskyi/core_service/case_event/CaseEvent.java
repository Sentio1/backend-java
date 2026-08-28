package com.lisovskyi.core_service.case_event;

import com.lisovskyi.core_service.case_.Case;
import com.lisovskyi.core_service.case_event.enums.EventCode;
import com.lisovskyi.core_service.case_event.enums.Source;
import com.lisovskyi.core_service.entity.CoreEntityCreatedOnly;
import com.lisovskyi.jpa.autoconfigure.generator.SequenceSize;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

import static com.lisovskyi.core_service.case_event.CaseEventConstants.*;

@Entity
@Table(name = "case_events")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@SequenceSize(size = 50)
public class CaseEvent extends CoreEntityCreatedOnly {

    @Column(name = "organization_id", nullable = false)
    private long organizationId;

    // фізичний FK у межах core-service: core.cases(id)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false, referencedColumnName = "id")
    private Case case_;

    // 'CLAIM_FILED', 'RULING_RECEIVED'
    @Enumerated(EnumType.STRING)
    @Column(name = "event_code", nullable = false, length = EVENT_CODE_LENGTH)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private EventCode eventCode;

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
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = SOURCE_LENGTH)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default
    private Source source = Source.MANUAL;

    // soft-ref на документ у Registry Monitor (окремий сервіс, не FK)
    @Column(name = "registry_document_id")
    private Long registryDocumentId;

    // soft-ref auth.users.id
    @Column(name = "created_by")
    private Long createdBy;
}

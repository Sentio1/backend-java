-- ═══════════════════════════════════════════════════════════
-- Міграція: core.case_events (SEN-17)
-- ═══════════════════════════════════════════════════════════
-- Хронологія справи — фіксує події, що запускають відлік строків.

CREATE SEQUENCE IF NOT EXISTS core.case_event_seq_gen START WITH 1 INCREMENT BY 50;

CREATE TABLE core.case_events (
    id                    BIGINT PRIMARY KEY,
    organization_id       BIGINT NOT NULL,
    case_id               BIGINT NOT NULL REFERENCES core.cases (id),
    event_code            VARCHAR(50) NOT NULL,   -- 'CLAIM_FILED', 'RULING_RECEIVED'
    title                 VARCHAR(255) NOT NULL,
    description           TEXT,
    occurred_at           TIMESTAMPTZ NOT NULL,   -- коли подія фактично сталася, від неї рахується строк
    registered_at         TIMESTAMPTZ,            -- коли зафіксована/отримана — може відрізнятись від occurred_at
    source                VARCHAR(20) NOT NULL DEFAULT 'MANUAL',  -- MANUAL | REGISTRY
    registry_document_id  BIGINT,                 -- soft-ref на документ у Registry Monitor (окремий сервіс)
    created_by            BIGINT,                 -- soft-ref auth.users.id
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at            TIMESTAMPTZ,
    deleted_by            BIGINT,
    delete_reason         VARCHAR(255)
);

CREATE INDEX case_events_organization_id_case_id_idx ON core.case_events (organization_id, case_id);
CREATE INDEX case_events_case_id_occurred_at_idx      ON core.case_events (case_id, occurred_at);

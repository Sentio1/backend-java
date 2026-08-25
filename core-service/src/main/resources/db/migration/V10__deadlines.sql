-- ═══════════════════════════════════════════════════════════
-- Міграція: core.deadlines (SEN-17)
-- ═══════════════════════════════════════════════════════════
-- Ядро продукту.

CREATE SEQUENCE IF NOT EXISTS core.deadlines_seq_gen START WITH 1 INCREMENT BY 50;

CREATE TABLE core.deadlines (
    id                   BIGINT PRIMARY KEY,
    organization_id      BIGINT NOT NULL,
    case_id              BIGINT NOT NULL REFERENCES core.cases (id),
    rule_id              BIGINT REFERENCES core.deadline_rules (id),
    triggering_event_id  BIGINT REFERENCES core.case_events (id),

    title                VARCHAR(255) NOT NULL,
    legal_basis          VARCHAR(255),  -- знімок на момент розрахунку
    starts_on            DATE NOT NULL,
    due_on                DATE NOT NULL, -- порахована дата
    status               core.deadline_status NOT NULL DEFAULT 'PENDING',
    completed_at         TIMESTAMPTZ,
    completed_by         BIGINT,        -- soft-ref auth.users.id
    extended_to          DATE,
    note                 TEXT,

    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at           TIMESTAMPTZ,
    deleted_by           BIGINT,
    delete_reason        VARCHAR(255)
);

-- головний запит календаря
CREATE INDEX deadlines_organization_id_status_due_on_idx ON core.deadlines (organization_id, status, due_on);
CREATE INDEX deadlines_case_id_due_on_idx                ON core.deadlines (case_id, due_on);
-- черга нагадувань
CREATE INDEX deadlines_status_due_on_idx                 ON core.deadlines (status, due_on);

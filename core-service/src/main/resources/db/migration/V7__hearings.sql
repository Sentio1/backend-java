-- ═══════════════════════════════════════════════════════════
-- Міграція: core.hearings (SEN-17)
-- ═══════════════════════════════════════════════════════════

CREATE SEQUENCE IF NOT EXISTS core.hearings_seq_gen START WITH 1 INCREMENT BY 50;

CREATE TABLE core.hearings (
    id                     BIGINT PRIMARY KEY,
    organization_id        BIGINT NOT NULL,
    case_id                BIGINT NOT NULL REFERENCES core.cases (id),
    scheduled_at           TIMESTAMPTZ NOT NULL,
    kind                   VARCHAR(50),   -- підготовче / по суті / апеляційне
    courtroom              VARCHAR(50),
    outcome                TEXT,          -- заповнюється після засідання -> йде у звіт клієнту
    reported_to_client_at  TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at             TIMESTAMPTZ,
    deleted_by             BIGINT,
    delete_reason          VARCHAR(255)
);

CREATE INDEX hearings_organization_id_case_id_idx ON core.hearings (organization_id, case_id);
CREATE INDEX hearings_case_id_scheduled_at_idx     ON core.hearings (case_id, scheduled_at);

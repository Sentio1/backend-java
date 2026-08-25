-- ═══════════════════════════════════════════════════════════
-- Міграція: core.cases (SEN-17)
-- ═══════════════════════════════════════════════════════════

CREATE SEQUENCE IF NOT EXISTS core.cases_seq_gen START WITH 1 INCREMENT BY 50;

CREATE TABLE core.cases (
    id                        BIGINT PRIMARY KEY,
    organization_id           BIGINT NOT NULL,      -- soft-ref auth.organizations.id
    responsible_user_id       BIGINT NOT NULL,      -- soft-ref auth.users.id

    case_number               VARCHAR(50),          -- 761/4823/25 — null поки не відкрито провадження
    internal_number           VARCHAR(50),          -- власна нумерація бюро
    title                     VARCHAR(255) NOT NULL,
    procedure                 core.procedure_type NOT NULL,
    status                    core.case_status NOT NULL DEFAULT 'DRAFT',
    court_id                  BIGINT REFERENCES core.courts (id),
    judge_name                VARCHAR(255),
    opened_at                 DATE,
    closed_at                 DATE,

    registry_watch_enabled    BOOLEAN NOT NULL DEFAULT false,
    registry_last_checked_at  TIMESTAMPTZ,

    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at                TIMESTAMPTZ,
    deleted_by                BIGINT,               -- soft-ref auth.users.id
    delete_reason             VARCHAR(255)
);

CREATE INDEX cases_organization_id_status_idx      ON core.cases (organization_id, status);
CREATE INDEX cases_organization_id_case_number_idx ON core.cases (organization_id, case_number);
CREATE INDEX cases_responsible_user_id_idx         ON core.cases (responsible_user_id);

-- черга поллінгу Go-воркера моніторингу реєстру
CREATE INDEX cases_registry_watch_queue_idx ON core.cases (registry_watch_enabled, registry_last_checked_at);

-- ═══════════════════════════════════════════════════════════
-- Міграція: core.clients (SEN-17)
-- ═══════════════════════════════════════════════════════════

CREATE SEQUENCE IF NOT EXISTS core.client_seq_gen START WITH 1 INCREMENT BY 50;

CREATE TABLE core.clients (
    id              BIGINT PRIMARY KEY,
    organization_id BIGINT NOT NULL,   -- soft-ref auth.organizations.id
    type            core.client_type NOT NULL DEFAULT 'INDIVIDUAL',

    -- фізична особа / ФОП
    last_name       VARCHAR(100),
    first_name      VARCHAR(100),
    middle_name     VARCHAR(100),
    birth_date      DATE,
    rnokpp          VARCHAR(10),          -- РНОКПП (ІПН)
    passport        VARCHAR(50),

    -- юридична особа
    company_name    VARCHAR(255),
    edrpou          VARCHAR(10),
    director_name   VARCHAR(255),         -- для преамбули договорів

    email           CITEXT,
    phone_number    VARCHAR(20),
    address         TEXT,
    notes           TEXT,

    created_by      BIGINT NOT NULL,      -- soft-ref auth.users.id
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,
    deleted_by      BIGINT,               -- soft-ref auth.users.id
    delete_reason   VARCHAR(255),
    restored_at     TIMESTAMPTZ,
    restored_by     BIGINT,               -- soft-ref auth.users.id

    CONSTRAINT clients_type_name_check CHECK (
        (type = 'COMPANY' AND company_name IS NOT NULL)
        OR (type <> 'COMPANY' AND last_name IS NOT NULL AND first_name IS NOT NULL)
    )
);

CREATE INDEX clients_organization_id_last_name_idx ON core.clients (organization_id, last_name);
CREATE INDEX clients_organization_id_rnokpp_idx     ON core.clients (organization_id, rnokpp);
CREATE INDEX clients_organization_id_edrpou_idx     ON core.clients (organization_id, edrpou);
CREATE INDEX clients_organization_id_idx            ON core.clients (organization_id);

-- Нечіткий пошук за прізвищем
CREATE INDEX clients_last_name_trgm_idx ON core.clients USING gin (last_name gin_trgm_ops);

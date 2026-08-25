-- ═══════════════════════════════════════════════════════════
-- Міграція: core.case_parties (SEN-17)
-- ═══════════════════════════════════════════════════════════
-- Сторони справи — заміна колишнього cases.client_id/client_role
-- на множинні сторони з роллю (позивач/відповідач/третя особа/...).

CREATE SEQUENCE IF NOT EXISTS core.case_parties_seq_gen START WITH 1 INCREMENT BY 50;

CREATE TABLE core.case_parties (
    id               BIGINT PRIMARY KEY,
    organization_id  BIGINT NOT NULL,
    case_id          BIGINT NOT NULL REFERENCES core.cases (id),
    client_id        BIGINT NOT NULL REFERENCES core.clients (id),
    role             core.case_party_role NOT NULL,
    is_primary       BOOLEAN NOT NULL DEFAULT false,  -- основний клієнт справи (біллінг/головний контакт)

    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ,
    deleted_by       BIGINT,
    delete_reason    VARCHAR(255)
);

CREATE INDEX case_parties_organization_id_case_id_idx   ON core.case_parties (organization_id, case_id);
CREATE INDEX case_parties_organization_id_client_id_idx ON core.case_parties (organization_id, client_id);

CREATE UNIQUE INDEX case_parties_case_id_client_id_role_idx ON core.case_parties (case_id, client_id, role);

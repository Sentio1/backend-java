-- ═══════════════════════════════════════════════════════════
-- Міграція: core.case_event_occurred_at_histories (SEN-19)
-- ═══════════════════════════════════════════════════════════
-- Правка occurred_at — найнебезпечніша операція в продукті: вона зсуває
-- всі похідні строки. Тому кожна зміна фіксується сюди зі старим і новим
-- значенням, автором і причиною, а не перезаписує occurred_at мовчки.

CREATE SEQUENCE IF NOT EXISTS core.case_event_occurred_at_history_seq_gen START WITH 1 INCREMENT BY 50;

CREATE TABLE core.case_event_occurred_at_histories (
    id                BIGINT PRIMARY KEY,
    case_event_id     BIGINT NOT NULL REFERENCES core.case_events (id),
    organization_id   BIGINT NOT NULL,
    old_occurred_at   TIMESTAMPTZ NOT NULL,
    new_occurred_at   TIMESTAMPTZ NOT NULL,
    changed_by        BIGINT NOT NULL,   -- soft-ref auth.users.id
    changed_at        TIMESTAMPTZ NOT NULL,
    reason            VARCHAR(255) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at        TIMESTAMPTZ,
    deleted_by        BIGINT,
    delete_reason     VARCHAR(255),
    restored_at       TIMESTAMPTZ,
    restored_by       BIGINT
);

CREATE INDEX case_event_occurred_at_histories_case_event_id_idx     ON core.case_event_occurred_at_histories (case_event_id);
CREATE INDEX case_event_occurred_at_histories_organization_id_idx  ON core.case_event_occurred_at_histories (organization_id);

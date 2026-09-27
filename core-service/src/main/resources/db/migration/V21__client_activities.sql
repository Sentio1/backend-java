CREATE SEQUENCE IF NOT EXISTS core.client_activity_seq_gen START WITH 1 INCREMENT BY 50;

CREATE TABLE core.client_activities (
    id              BIGINT PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    client_id       BIGINT NOT NULL REFERENCES core.clients(id),
    activity        VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX client_activities_client_id_idx ON core.client_activities (client_id);
CREATE INDEX client_activities_activity_trgm_idx ON core.client_activities USING gin (activity gin_trgm_ops);
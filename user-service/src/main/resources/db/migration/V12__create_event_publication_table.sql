-- Persistent event publication registry for Spring Modulith's @ApplicationModuleListener
-- (spring-modulith-events-jdbc). Schema matches Modulith's own official Postgres schema (v2)
-- for this module, just relocated into auth like every other table here.
CREATE TABLE auth.event_publication
(
    id                     UUID NOT NULL,
    listener_id            TEXT NOT NULL,
    event_type             TEXT NOT NULL,
    serialized_event       TEXT NOT NULL,
    publication_date       TIMESTAMPTZ NOT NULL,
    completion_date        TIMESTAMPTZ,
    status                 TEXT,
    completion_attempts    INT,
    last_resubmission_date TIMESTAMPTZ,
    PRIMARY KEY (id)
);

CREATE INDEX event_publication_serialized_event_hash_idx
    ON auth.event_publication USING hash (serialized_event);

CREATE INDEX event_publication_by_completion_date_idx
    ON auth.event_publication (completion_date);

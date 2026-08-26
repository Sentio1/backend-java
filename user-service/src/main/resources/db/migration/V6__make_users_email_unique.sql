-- Build the replacement partial unique index CONCURRENTLY (no blocking of concurrent
-- inserts/updates/deletes on users while it builds - a plain CREATE UNIQUE INDEX takes
-- a lock that would stall auth/profile writes on a populated table) and BEFORE dropping
-- the old constraint, so email uniqueness stays enforced for the whole transition instead
-- of leaving a gap where two concurrent inserts could both get in.
CREATE UNIQUE INDEX CONCURRENTLY users_email_active_idx ON users(email) WHERE deleted_at IS NULL;

alter table users drop constraint users_email_key;

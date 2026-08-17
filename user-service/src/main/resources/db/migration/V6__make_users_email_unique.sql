alter table users drop constraint users_email_key;

CREATE UNIQUE INDEX users_email_active_idx ON users(email) WHERE deleted_at IS NULL;
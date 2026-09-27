CREATE TYPE auth.user_action_token_type AS ENUM ('EMAIL_VERIFICATION', 'PASSWORD_RESET');

CREATE SEQUENCE IF NOT EXISTS auth.user_action_token_seq_gen START WITH 1 INCREMENT BY 50;

CREATE TABLE auth.user_action_tokens (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL
        REFERENCES auth.users(id) ON DELETE CASCADE,
    hash VARCHAR(64) NOT NULL UNIQUE,
    token_type auth.user_action_token_type NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    invalidated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_user_action_tokens_active_by_user
    ON auth.user_action_tokens(user_id, token_type)
    WHERE used_at IS NULL
    AND invalidated_at IS NULL;

CREATE INDEX idx_user_action_tokens_expires_at
    ON auth.user_action_tokens(expires_at);
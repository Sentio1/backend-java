CREATE SEQUENCE IF NOT EXISTS user_seq_gen                START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS user_identity_seq_gen       START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS organization_seq_gen        START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS organization_member_seq_gen START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS refresh_token_seq_gen       START WITH 1 INCREMENT BY 50;

CREATE TABLE users (
    id                  BIGINT PRIMARY KEY,
    email               CITEXT NOT NULL UNIQUE,
    password_hash       VARCHAR(72),           -- null для чистого OAuth; bcrypt = 60 символів
    phone_number        VARCHAR(20) UNIQUE,    -- E.164, nullable
    platform_role       platform_role NOT NULL DEFAULT 'USER',

    last_name           VARCHAR(100),
    first_name          VARCHAR(100),
    middle_name         VARCHAR(100),

    email_verified_at   TIMESTAMPTZ,
    phone_verified_at   TIMESTAMPTZ,
    last_login_at       TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ
);

CREATE TABLE organizations (
    id                      BIGINT PRIMARY KEY,
    name                    VARCHAR(255) NOT NULL,
    slug                    VARCHAR(100) NOT NULL UNIQUE,
    edrpou                  VARCHAR(10),                 -- ЄДРПОУ бюро, якщо юрособа
    plan                    plan_tier NOT NULL DEFAULT 'SOLO',
    subscription_status     subscription_status NOT NULL DEFAULT 'TRIALING',
    trial_ends_at           TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at              TIMESTAMPTZ
);

CREATE TABLE user_identities (
    id                  BIGINT PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    provider            auth_provider NOT NULL,
    provider_user_id    VARCHAR(255) NOT NULL,   -- sub із токена
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX user_identities_provider_provider_user_id_idx
    ON user_identities (provider, provider_user_id);

CREATE UNIQUE INDEX user_identities_user_id_provider_idx
    ON user_identities (user_id, provider);

CREATE TABLE organization_members (
    id                  BIGINT PRIMARY KEY,
    organization_id     BIGINT NOT NULL REFERENCES organizations(id),
    user_id             BIGINT NOT NULL REFERENCES users(id),
    role                org_role NOT NULL DEFAULT 'LAWYER',
    invited_by          BIGINT REFERENCES users(id),
    joined_at           TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX organization_members_org_user_idx
    ON organization_members (organization_id, user_id);

CREATE INDEX organization_members_organization_id_idx
    ON organization_members (organization_id);

CREATE INDEX organization_members_user_id_idx
    ON organization_members (user_id);


CREATE TABLE refresh_tokens (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    token_hash      VARCHAR(64) NOT NULL UNIQUE,  -- зберігаємо хеш, не токен
    user_agent      VARCHAR(255),
    ip              INET,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX refresh_tokens_user_id_idx
    ON refresh_tokens (user_id);

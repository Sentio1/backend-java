-- ═══════════════════════════════════════════════════════════
-- Міграція: перенесення auth-об'єктів у власну схему `auth`
-- ═══════════════════════════════════════════════════════════
-- Узгоджує структуру user-service з core-service (там уже core.*,
-- див. core-service/.../V1__create_custom_enums.sql) — кожен сервіс
-- тепер має власну іменовану схему замість спільного public.

CREATE SCHEMA IF NOT EXISTS auth;

-- Enum-типи, якими реально користуються auth-таблиці
ALTER TYPE platform_role       SET SCHEMA auth;
ALTER TYPE org_role            SET SCHEMA auth;
ALTER TYPE auth_provider       SET SCHEMA auth;
ALTER TYPE plan_tier           SET SCHEMA auth;
ALTER TYPE subscription_status SET SCHEMA auth;

-- Незадіяні типи з V2__create_custom_enums.sql — заготовка під
-- продуктову схему, яка тепер реалізується в core-service
-- (core.client_type тощо). Жодна auth-таблиця їх не використовує,
-- тож тут вони мертві — прибираємо, а не тягнемо як баласт.
DROP TYPE client_type;
DROP TYPE procedure_type;
DROP TYPE case_status;
DROP TYPE day_kind;
DROP TYPE deadline_status;
DROP TYPE notification_channel;
DROP TYPE notification_status;

-- Таблиці
ALTER TABLE users                SET SCHEMA auth;
ALTER TABLE organizations        SET SCHEMA auth;
ALTER TABLE user_identities      SET SCHEMA auth;
ALTER TABLE organization_members SET SCHEMA auth;
ALTER TABLE organization_invites SET SCHEMA auth;
ALTER TABLE refresh_tokens       SET SCHEMA auth;

-- Послідовності (sequence-based id з lisovskyi-jpa-starter)
ALTER SEQUENCE user_seq_gen                SET SCHEMA auth;
ALTER SEQUENCE user_identity_seq_gen       SET SCHEMA auth;
ALTER SEQUENCE organization_seq_gen        SET SCHEMA auth;
ALTER SEQUENCE organization_member_seq_gen SET SCHEMA auth;
ALTER SEQUENCE organization_invite_seq_gen SET SCHEMA auth;
ALTER SEQUENCE refresh_token_seq_gen       SET SCHEMA auth;

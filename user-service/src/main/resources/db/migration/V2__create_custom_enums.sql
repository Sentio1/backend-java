-- ═══════════════════════════════════════════════════════════
-- Міграція: Створення Enum типів для бази даних Sentio
-- ═══════════════════════════════════════════════════════════

CREATE TYPE platform_role AS ENUM (
    'ADMIN',
    'USER'
);

CREATE TYPE org_role AS ENUM (
    'OWNER',
    'LAWYER',
    'ASSISTANT'
);

CREATE TYPE auth_provider AS ENUM (
    'LOCAL',
    'GOOGLE',
    'APPLE'
);

CREATE TYPE client_type AS ENUM (
    'INDIVIDUAL',
    'SOLE_TRADER',
    'COMPANY'
);

CREATE TYPE procedure_type AS ENUM (
    'CIVIL',
    'ADMINISTRATIVE',
    'COMMERCIAL',
    'CRIMINAL',
    'OTHER'
);

CREATE TYPE case_status AS ENUM (
    'DRAFT',
    'PRE_TRIAL',
    'FIRST_INSTANCE',
    'APPEAL',
    'CASSATION',
    'ENFORCEMENT',
    'CLOSED',
    'ARCHIVED'
);

CREATE TYPE day_kind AS ENUM (
    'CALENDAR',
    'WORKING'
);

CREATE TYPE deadline_status AS ENUM (
    'PENDING',
    'DONE',
    'MISSED',
    'SUSPENDED',
    'EXTENDED'
);

CREATE TYPE notification_channel AS ENUM (
    'EMAIL',
    'TELEGRAM',
    'PUSH',
    'IN_APP'
);

CREATE TYPE notification_status AS ENUM (
    'QUEUED',
    'SENT',
    'FAILED',
    'READ'
);

CREATE TYPE plan_tier AS ENUM (
    'SOLO',
    'BUREAU',
    'FIRM'
);

CREATE TYPE subscription_status AS ENUM (
    'TRIALING',
    'ACTIVE',
    'PAST_DUE',
    'CANCELED'
);
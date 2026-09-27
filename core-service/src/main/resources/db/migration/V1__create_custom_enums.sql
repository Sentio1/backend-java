-- ═══════════════════════════════════════════════════════════
-- Міграція: схема core + Enum типи для core-service (SEN-17)
-- ═══════════════════════════════════════════════════════════
-- Окрема БД/сервіс від user-service (auth), тому типи з
-- V2__create_custom_enums.sql user-service тут не діють і
-- визначаються заново, схемо-кваліфіковано під core.

CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE SCHEMA IF NOT EXISTS core;

CREATE TYPE core.client_type AS ENUM (
    'INDIVIDUAL',
    'SOLE_TRADER',
    'COMPANY'
);

CREATE TYPE core.procedure_type AS ENUM (
    'CIVIL',
    'ADMINISTRATIVE',
    'COMMERCIAL',
    'CRIMINAL',
    'OTHER'
);

CREATE TYPE core.case_status AS ENUM (
    'DRAFT',
    'PRE_TRIAL',
    'FIRST_INSTANCE',
    'APPEAL',
    'CASSATION',
    'ENFORCEMENT',
    'CLOSED',
    'ARCHIVED'
);

-- NEW: раніше було вільне поле cases.client_role varchar(50)
CREATE TYPE core.case_party_role AS ENUM (
    'PLAINTIFF',
    'DEFENDANT',
    'THIRD_PARTY',
    'REPRESENTATIVE',
    'OTHER'
);

CREATE TYPE core.day_kind AS ENUM (
    'CALENDAR',
    'WORKING'
);

CREATE TYPE core.deadline_status AS ENUM (
    'PENDING',
    'DONE',
    'MISSED',
    'SUSPENDED',
    'EXTENDED'
);

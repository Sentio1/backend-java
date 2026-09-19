-- ═══════════════════════════════════════════════════════════
-- Міграція: core.deadline_rules (SEN-17)
-- ═══════════════════════════════════════════════════════════
-- Довідник правил, версіонований — редакції кодексів змінюються,
-- і старі справи не мають "переїжджати" на нові строки.
-- БЕЗ organization_id: правила спільні для всіх орендарів.

CREATE SEQUENCE IF NOT EXISTS core.deadline_rule_seq_gen START WITH 1 INCREMENT BY 50;

CREATE TABLE core.deadline_rules (
    id                  BIGINT PRIMARY KEY,
    code                VARCHAR(50) NOT NULL,   -- 'CPC_STATEMENT_OF_DEFENCE'
    procedure           core.procedure_type NOT NULL,
    trigger_event_code  VARCHAR(50) NOT NULL,   -- яка подія запускає відлік
    title               VARCHAR(255) NOT NULL,
    legal_basis         VARCHAR(255) NOT NULL,  -- 'ст. 178 ЦПК України'
    duration_value      SMALLINT NOT NULL,
    duration_unit       VARCHAR(10) NOT NULL DEFAULT 'DAY',       -- DAY | MONTH
    day_kind            core.day_kind NOT NULL DEFAULT 'CALENDAR',
    count_from          VARCHAR(20) NOT NULL DEFAULT 'NEXT_DAY',  -- строк тече з наступного дня
    is_extendable       BOOLEAN NOT NULL DEFAULT false,
    valid_from          DATE NOT NULL,
    valid_to            DATE   -- null = чинне
);

CREATE UNIQUE INDEX deadline_rules_code_valid_from_idx ON core.deadline_rules (code, valid_from);

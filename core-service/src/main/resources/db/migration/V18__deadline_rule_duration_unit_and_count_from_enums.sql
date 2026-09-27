-- ═══════════════════════════════════════════════════════════
-- Міграція: deadline_rules.duration_unit / count_from -> enum
-- ═══════════════════════════════════════════════════════════
-- DeadlineRule.durationUnit і DeadlineRule.countFrom тепер типізовані enum'и
-- (@JdbcTypeCode(SqlTypes.NAMED_ENUM)) замість вільного VARCHAR, за тим самим
-- підходом, що й hearings.kind/deadline_rules.trigger_event_code у V16.

CREATE TYPE core.duration_unit AS ENUM (
    'DAY',
    'MONTH'
);

CREATE TYPE core.count_from AS ENUM (
    'NEXT_DAY',
    'SAME_DAY'
);

-- DEFAULT знімається перед ALTER TYPE — Postgres не вміє автоматично привести
-- старий текстовий default до нового enum-типу в одному кроці.
ALTER TABLE core.deadline_rules
    ALTER COLUMN duration_unit DROP DEFAULT,
    ALTER COLUMN duration_unit TYPE core.duration_unit USING duration_unit::core.duration_unit,
    ALTER COLUMN duration_unit SET DEFAULT 'DAY';

ALTER TABLE core.deadline_rules
    ALTER COLUMN count_from DROP DEFAULT,
    ALTER COLUMN count_from TYPE core.count_from USING count_from::core.count_from,
    ALTER COLUMN count_from SET DEFAULT 'NEXT_DAY';

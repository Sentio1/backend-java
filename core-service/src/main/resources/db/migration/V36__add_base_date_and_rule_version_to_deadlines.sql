-- ═══════════════════════════════════════════════════════════
-- Міграція: core.deadlines add base_date and rule_version
-- ═══════════════════════════════════════════════════════════

ALTER TABLE core.deadlines
    ADD COLUMN base_date DATE,
    ADD COLUMN rule_version SMALLINT NOT NULL DEFAULT 1;

-- Знімаємо DEFAULT
ALTER TABLE core.deadlines
    ALTER COLUMN rule_version DROP DEFAULT;

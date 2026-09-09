-- ═══════════════════════════════════════════════════════════
-- Міграція: core.holidays (SEN-17)
-- ═══════════════════════════════════════════════════════════
-- БЕЗ organization_id: спільний виробничий календар для всіх орендарів.

CREATE TABLE core.holidays (
    date       DATE PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    is_working BOOLEAN NOT NULL DEFAULT false  -- перенесені робочі суботи
);

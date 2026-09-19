-- ═══════════════════════════════════════════════════════════
-- Міграція: core.courts (SEN-17)
-- ═══════════════════════════════════════════════════════════
-- Глобальний довідник судів — БЕЗ organization_id, суди спільні
-- для всіх орендарів, це не дані організації.

CREATE SEQUENCE IF NOT EXISTS core.court_seq_gen START WITH 1 INCREMENT BY 50;

CREATE TABLE core.courts (
    id        BIGINT PRIMARY KEY,
    name      VARCHAR(255) NOT NULL,
    code      VARCHAR(20) UNIQUE,     -- код суду з номера справи: 761, 522
    instance  SMALLINT NOT NULL,      -- 1 / 2 / 3
    region    VARCHAR(100),
    address   TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true
);

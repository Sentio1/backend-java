-- ═══════════════════════════════════════════════════════════
-- Міграція: courts.instance -> enum (SEN-21)
-- ═══════════════════════════════════════════════════════════
-- Court.instance був голим SMALLINT (1/2/3, див. коментар у V3__courts.sql) - тепер
-- типізований core_service.court.CourtInstance (@JdbcTypeCode(SqlTypes.NAMED_ENUM)), той
-- самий підхід, що й hearings.kind у V16. Назва колонки лишається "instance" (Java-поле
-- перейменоване на courtInstance через @Column(name = "instance")).
--
-- Без ELSE в CASE: раніше нічого не обмежувало instance значеннями 1-3, тож будь-яке інше
-- число впаде в NULL -> NOT NULL constraint на ALTER явно провалить міграцію замість
-- мовчки підставити довільне значення.

CREATE TYPE core.court_instance AS ENUM (
    'FIRST',
    'APPEAL',
    'CASSATION'
);

ALTER TABLE core.courts
    ALTER COLUMN instance TYPE core.court_instance
    USING (
        CASE instance
            WHEN 1 THEN 'FIRST'
            WHEN 2 THEN 'APPEAL'
            WHEN 3 THEN 'CASSATION'
        END
    )::core.court_instance;

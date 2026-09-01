-- ═══════════════════════════════════════════════════════════
-- Міграція: cases.instance (нова колонка) + case_status -> workflow-статус (SEN-21)
-- ═══════════════════════════════════════════════════════════
-- Раніше "інстанція" і "процесуальна стадія" були змішані в одному core.case_status
-- (DRAFT/PRE_TRIAL/FIRST_INSTANCE/APPEAL/CASSATION/ENFORCEMENT/CLOSED/ARCHIVED). SEN-21
-- розділяє їх: інстанція - окрема колонка/enum на cases (може змінюватись незалежно від
-- статусу, коли справа переходить із першої інстанції в апеляційну й далі - той самий
-- номер справи, нова інстанція), а статус звужується до чотирьох workflow-значень з AC.

CREATE TYPE core.case_instance AS ENUM (
    'FIRST',
    'APPEAL',
    'CASSATION'
);

-- DEFAULT 'FIRST' лише щоб додавання NOT NULL-колонки не впало, якщо в таблиці вже є
-- рядки (локально/на dev) - на рівні застосунку instance завжди обов'язкове поле запиту
-- (немає @Builder.Default на Case.instance, на відміну від procedure), тому дефолт
-- одразу знімаємо, а не лишаємо як мовчазну підстановку для майбутніх insert.
ALTER TABLE core.cases ADD COLUMN instance core.case_instance NOT NULL DEFAULT 'FIRST';
ALTER TABLE core.cases ALTER COLUMN instance DROP DEFAULT;

-- ── статус: старий 8-значеннєвий enum -> новий 4-значеннєвий (AC SEN-21) ──────────────
ALTER TYPE core.case_status RENAME TO case_status_old;

CREATE TYPE core.case_status AS ENUM (
    'ACTIVE',
    'SUSPENDED',
    'COMPLETED',
    'ARCHIVED'
);

ALTER TABLE core.cases ALTER COLUMN status DROP DEFAULT;

-- Мапінг старих значень: CLOSED -> COMPLETED (перейменування), ARCHIVED лишається
-- ARCHIVED, решта (DRAFT/PRE_TRIAL/FIRST_INSTANCE/APPEAL/CASSATION/ENFORCEMENT - це були
-- процесуальні стадії, не workflow-статуси) -> ACTIVE, бо в новій моделі "справа ще
-- триває" це й означає. На момент цієї міграції реальних даних ще ніде немає, тому цей
-- мапінг - лише страховка на випадок ручного сідингу, а не міграція прод-даних.
ALTER TABLE core.cases
    ALTER COLUMN status TYPE core.case_status
    USING (
        CASE status::text
            WHEN 'CLOSED' THEN 'COMPLETED'
            WHEN 'ARCHIVED' THEN 'ARCHIVED'
            ELSE 'ACTIVE'
        END
    )::core.case_status;

ALTER TABLE core.cases ALTER COLUMN status SET DEFAULT 'ACTIVE'::core.case_status;

DROP TYPE core.case_status_old;

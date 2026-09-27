-- ═══════════════════════════════════════════════════════════
-- Міграція: core.deadlines - source (MANUAL) + nullable rule_version (SEN-29 AC3)
-- ═══════════════════════════════════════════════════════════
-- Ручний строк (юрист додає його сам, без DeadlineRule): Deadline.source тепер Java-енум
-- DeadlineSource з @JdbcTypeCode(SqlTypes.NAMED_ENUM) - за тим самим правилом іменування, що
-- вже описане у V14 (Hibernate резолвить назву типу як snake_case ПРОСТОГО ІМЕНІ КЛАСУ через
-- SpringPhysicalNamingStrategy: DeadlineSource -> deadline_source, незалежно від назви колонки
-- чи пакету).
CREATE TYPE core.deadline_source AS ENUM (
    'RULE',
    'MANUAL'
);

-- DEFAULT лише щоб ADD COLUMN NOT NULL не впав на вже наявних рядках (усі вони - результат
-- DeadlineEngine, тобто RULE) - той самий підхід, що й у V30/V36. На рівні застосунку
-- DeadlineEngine.generateDeadlineForRule і DeadlineService.createManualDeadline обидва
-- завжди виставляють source явно, тому дефолт одразу знімаємо.
ALTER TABLE core.deadlines
    ADD COLUMN source core.deadline_source NOT NULL DEFAULT 'RULE';

ALTER TABLE core.deadlines
    ALTER COLUMN source DROP DEFAULT;

-- rule_version (V36) був NOT NULL, бо на момент його додавання ЄДИНИЙ спосіб отримати
-- Deadline - через DeadlineEngine (завжди зі знімком версії правила). Ручний дедлайн (AC3)
-- не має жодного DeadlineRule, тож і версії правила знімати нема з чого - той самий "снапшот
-- застосовний лише для RULE" статус, що вже мають duration_value/duration_unit/day_kind/
-- base_date/naive_due_on (усі nullable з тієї ж причини, деякі - ще з V36-V38).
ALTER TABLE core.deadlines
    ALTER COLUMN rule_version DROP NOT NULL;

-- Явний інваріант АС3 на рівні БД, а не лише в Java: RULE-дедлайн завжди має rule_id (інакше
-- нема звідки взявся розрахунок), MANUAL - ніколи. NOT VALID - як і в V23 (не блокує rollout
-- на вже наявних даних, лише пильнує нові й майбутні UPDATE/INSERT).
ALTER TABLE core.deadlines
    ADD CONSTRAINT deadlines_source_rule_id_consistency_check
        CHECK (
            (source = 'RULE' AND rule_id IS NOT NULL)
                OR (source = 'MANUAL' AND rule_id IS NULL)
            ) NOT VALID;

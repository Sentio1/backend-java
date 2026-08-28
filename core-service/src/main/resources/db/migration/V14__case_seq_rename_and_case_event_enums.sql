-- ═══════════════════════════════════════════════════════════
-- Міграція: case_seq_gen + enum-и для case_events (SEN-19)
-- ═══════════════════════════════════════════════════════════

-- Сутність CaseEntity перейменована на Case. lisovskyi-jpa-starter виводить назву
-- послідовності з простого імені класу (CamelCase -> snake_case + "_seq_gen"), тому
-- @SequenceSize тепер очікує core.case_seq_gen, а не core.case_entity_seq_gen,
-- створену в V4__cases.sql. Дані не займає - просто RENAME, той самий лічильник.
ALTER SEQUENCE core.case_entity_seq_gen RENAME TO case_seq_gen;

-- event_code/source у CaseEvent тепер Java-енуми (EventCode, Source) з
-- @JdbcTypeCode(SqlTypes.NAMED_ENUM) - так само, як ClientType/CaseStatus/etc.
-- у V1__create_custom_enums.sql. Hibernate резолвить назву типу як snake_case
-- простого імені класу через SpringPhysicalNamingStrategy: EventCode -> event_code,
-- Source -> source.
CREATE TYPE core.event_code AS ENUM (
    'CLAIM_FILED',
    'RULING_RECEIVED'
);

CREATE TYPE core.source AS ENUM (
    'MANUAL',
    'REGISTRY'
);

-- DEFAULT на varchar-колонці не кастується автоматично під час зміни типу -
-- знімаємо, міняємо тип, повертаємо назад уже під новий enum.
ALTER TABLE core.case_events ALTER COLUMN source DROP DEFAULT;

ALTER TABLE core.case_events
    ALTER COLUMN event_code TYPE core.event_code USING event_code::core.event_code,
    ALTER COLUMN source TYPE core.source USING source::core.source;

ALTER TABLE core.case_events ALTER COLUMN source SET DEFAULT 'MANUAL'::core.source;

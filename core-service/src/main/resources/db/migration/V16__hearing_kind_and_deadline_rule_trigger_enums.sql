-- ═══════════════════════════════════════════════════════════
-- Міграція: hearings.kind і deadline_rules.trigger_event_code -> enum
-- ═══════════════════════════════════════════════════════════

-- Hearing.kind тепер HearingKind (@JdbcTypeCode(SqlTypes.NAMED_ENUM)) замість
-- вільного тексту "підготовче / по суті / апеляційне".
CREATE TYPE core.hearing_kind AS ENUM (
    'PREPARATORY',
    'ON_MERITS',
    'APPEAL'
);

ALTER TABLE core.hearings
    ALTER COLUMN kind TYPE core.hearing_kind USING kind::core.hearing_kind;

-- DeadlineRule.triggerEventCode тепер типізований як core_service.case_event.enums.EventCode -
-- той самий enum, що й case_events.event_code (V14) - а не вільний VARCHAR("яка подія запускає
-- відлік" мало відповідати конкретним значенням event_code і раніше). Тип core.event_code вже
-- створено в V14, тут лише переводимо колонку.
ALTER TABLE core.deadline_rules
    ALTER COLUMN trigger_event_code TYPE core.event_code USING trigger_event_code::core.event_code;

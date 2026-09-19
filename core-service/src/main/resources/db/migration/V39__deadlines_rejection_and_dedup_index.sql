-- ═══════════════════════════════════════════════════════════
-- Міграція: core.deadlines - відхилення строку + унікальність по (triggering_event_id, rule_id) (SEN-29)
-- ═══════════════════════════════════════════════════════════
-- AC4: юрист може відхилити автоматично створений строк з причиною, запис лишається в історії.
-- rejected_at/rejected_by/rejection_reason - знімок самого факту на Deadline (audit_log описує
-- те саме детальніше, як і з occurredAt/dueOn - див. DeadlineEngine/CaseEventService), REJECTED -
-- новий термінальний статус поруч із DONE/MISSED.
ALTER TYPE core.deadline_status ADD VALUE 'REJECTED';

ALTER TABLE core.deadlines
    ADD COLUMN rejected_at      TIMESTAMPTZ,
    ADD COLUMN rejected_by      BIGINT,       -- soft-ref auth.users.id
    ADD COLUMN rejection_reason VARCHAR(500);

-- AC5: повторне збереження тієї самої події не повинно давати другий Deadline для того самого
-- застосовного правила. AC1 дозволив одній події породжувати кілька дедлайнів (по одному на
-- кожен рядок DeadlineRule, що підходить під пару процедура+тип події) - тож унікальність тепер
-- на парі (triggering_event_id, rule_id), а не на самому triggering_event_id: два різних правила
-- для тієї самої події - це два законних рядки, а не дублікат. DeadlineEngine.generateDeadline
-- уже перевіряє це в Java (перевірка по DeadlineRule.code перед insert - findAllByTriggeringEvent
-- + reconciliation), але без обмеження в БД паралельний повторний виклик (напр. Registry Monitor
-- ретраїть той самий webhook одночасно двома запитами) міг би проскочити між SELECT і INSERT і
-- завести дублікат - саме цей індекс і ловить таку гонку (DeadlineEngine.saveWithRaceRecovery).
-- Частковий індекс, а не звичайний UNIQUE-констрейнт на колонках:
--   - WHERE triggering_event_id IS NOT NULL - ручні строки без тригер-події (AC3, ще не
--     реалізовано) матимуть NULL тут, і кілька ручних строків на ту саму справу - це нормально,
--     NULL != NULL для UNIQUE все одно б це дозволив, але явна умова тут документує намір;
--   - WHERE deleted_at IS NULL - deleteCaseEvent (SoftDeleteManager) софт-видаляє й дедлайни
--     разом з подією; без цієї умови повторна реєстрація події з тим самим id (після restore)
--     чи новий дедлайн на нову версію події конфліктували б із уже видаленим рядком.
CREATE UNIQUE INDEX uq_deadlines_triggering_event_rule
    ON core.deadlines (triggering_event_id, rule_id)
    WHERE triggering_event_id IS NOT NULL AND deleted_at IS NULL;

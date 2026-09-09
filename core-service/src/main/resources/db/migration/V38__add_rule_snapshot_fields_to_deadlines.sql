-- ═══════════════════════════════════════════════════════════
-- Міграція: core.deadlines add duration_value/duration_unit/day_kind
-- ═══════════════════════════════════════════════════════════

-- Знімок арифметики правила на момент розрахунку (SEN-28), поруч з уже наявними title/
-- legal_basis. Потрібно для людського пояснення ("30 календарних днів від ...") без
-- живого читання deadline_rules - той рядок можна відредагувати на місці
-- (DeadlineRuleService.updateDeadlineRule), і "жива" durationValue могла б розійтися з тим,
-- що реально застосувалось. NULL для рядків, порахованих до SEN-28, - заднім числом
-- не відновлюється.
ALTER TABLE core.deadlines
    ADD COLUMN duration_value SMALLINT,
    ADD COLUMN duration_unit  core.duration_unit,
    ADD COLUMN day_kind       core.day_kind;

-- ═══════════════════════════════════════════════════════════
-- Міграція: core.deadlines.created_by (SEN-29 AC3)
-- ═══════════════════════════════════════════════════════════
-- Ручний строк (source = MANUAL) - юрист створює його явним викликом
-- DeadlineService.createManualDeadline, тож, на відміну від RULE-дедлайнів (їх "створює" подія/
-- DeadlineEngine, а не людина напряму), тут є кого записати як автора. Nullable й без DEFAULT -
-- для RULE-дедлайнів (уже наявних і майбутніх) поле завжди залишається null, це не прогалина в
-- даних, а частина інваріанту (як rule_id для MANUAL).
ALTER TABLE core.deadlines
    ADD COLUMN created_by BIGINT;

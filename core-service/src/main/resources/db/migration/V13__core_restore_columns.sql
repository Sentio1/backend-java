-- ═══════════════════════════════════════════════════════════
-- Міграція: restored_at/restored_by у решті таблиць core (SEN-17)
-- ═══════════════════════════════════════════════════════════
-- core.clients вже має ці колонки (V2). Той самий шаблон
-- "soft-delete + restore" поширюємо на решту таблиць схеми, що мають
-- deleted_at, — щоб вони могли успадковувати спільні
-- com.lisovskyi.core_service.entity.CoreEntity / CoreEntityCreatedOnly.
-- Довідникові таблиці без deleted_at (courts, deadline_rules, holidays)
-- свідомо не чіпаються.

ALTER TABLE core.cases        ADD COLUMN restored_at TIMESTAMPTZ, ADD COLUMN restored_by BIGINT;
ALTER TABLE core.case_parties ADD COLUMN restored_at TIMESTAMPTZ, ADD COLUMN restored_by BIGINT;
ALTER TABLE core.case_events  ADD COLUMN restored_at TIMESTAMPTZ, ADD COLUMN restored_by BIGINT;
ALTER TABLE core.hearings     ADD COLUMN restored_at TIMESTAMPTZ, ADD COLUMN restored_by BIGINT;
ALTER TABLE core.deadlines    ADD COLUMN restored_at TIMESTAMPTZ, ADD COLUMN restored_by BIGINT;

-- ═══════════════════════════════════════════════════════════
-- Міграція: cases.created_by (SEN-21)
-- ═══════════════════════════════════════════════════════════
-- Той самий soft-ref-патерн, що й Client.createdBy (auth.users.id, без FK - окрема БД).
-- Спочатку nullable, бекфіл responsible_user_id як найкраще наближення для вже наявних
-- рядків (creator і responsible - здебільшого одна й та сама людина в невеликій юрфірмі),
-- потім SET NOT NULL. Якщо таблиця порожня (testcontainers/чистий dev) - UPDATE просто
-- нічого не робить.

ALTER TABLE core.cases ADD COLUMN created_by BIGINT;

UPDATE core.cases SET created_by = responsible_user_id WHERE created_by IS NULL;

ALTER TABLE core.cases ALTER COLUMN created_by SET NOT NULL;

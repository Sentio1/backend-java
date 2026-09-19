-- ═══════════════════════════════════════════════════════════
-- Міграція: core.audit_logs — changed_by_type (SEN-23 follow-up)
-- ═══════════════════════════════════════════════════════════
-- Досі DeadlineEngine при системному перерахунку dueOn (напр. зміна виробничого календаря,
-- DeadlineListener) просто НЕ писав рядок аудиту, коли не було винної людини (changedBy = null) -
-- бо changed_by був NOT NULL soft-ref на auth.users.id, і вигадувати для цього фіктивного
-- користувача не було сенсу. Наслідок: в історії дедлайна - дірка, зміна dueOn без жодного сліду.
--
-- Рішення - не фейковий рядок в auth.users, а чесний тип автора: changed_by_type розрізняє
-- USER (changed_by тоді й далі реальний auth.users.id) і SYSTEM (changed_by = null - автоматичний
-- процес, за який не відповідає конкретна людина). Існуючі рядки завжди мали людину-автора, тому
-- бекфіляться як USER.

CREATE TYPE core.changed_by_type AS ENUM (
    'USER',
    'SYSTEM'
);

ALTER TABLE core.audit_logs
    ADD COLUMN changed_by_type core.changed_by_type;

UPDATE core.audit_logs
SET changed_by_type = 'USER'
WHERE changed_by_type IS NULL;

ALTER TABLE core.audit_logs
    ALTER COLUMN changed_by_type SET NOT NULL,
    ALTER COLUMN changed_by DROP NOT NULL;

-- SYSTEM-рядки не мають автора; USER-рядки мусять його мати - без цього NOT NULL на самому
-- changed_by можна було б обійти, лишивши null і для USER.
ALTER TABLE core.audit_logs
    ADD CONSTRAINT audit_logs_changed_by_matches_type CHECK (
        (changed_by_type = 'USER' AND changed_by IS NOT NULL) OR
        (changed_by_type = 'SYSTEM' AND changed_by IS NULL)
    );

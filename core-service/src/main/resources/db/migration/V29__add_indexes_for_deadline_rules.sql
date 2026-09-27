CREATE EXTENSION IF NOT EXISTS btree_gist;

-- core.deadline_rules, а не голе deadline_rules: усі інші міграції в цьому проєкті
-- завжди схемо-кваліфіковані (див. коментар у V1), і тут немає жодного search_path,
-- який зробив би голе "deadline_rules" синонімом core.deadline_rules - без префіксу
-- ALTER TABLE впала б з "relation does not exist" на чистій БД (звичайний search_path
-- Postgres - "$user", public).
-- DEFERRABLE INITIALLY DEFERRED: DeadlineRuleService.createDeadlineRule закриває
-- попередню версію (UPDATE validTo) і зберігає нову (INSERT) в одній транзакції, але
-- Hibernate групує flush за типом дії й виконує ВСІ INSERT перед ВСІМА UPDATE незалежно
-- від порядку викликів у Java-коді. Без DEFERRABLE Postgres перевіряє exclusion
-- constraint одразу після INSERT нового рядка - у цей момент старий рядок ще не закритий
-- (validTo щойно виставили в Java-об'єкті, але UPDATE ще не пішов у БД) і два відкритих
-- діапазони для одного code завжди конфліктують, навіть коли фінальний стан коректний.
-- DEFERRED переносить перевірку на COMMIT, коли обидва зміни вже застосовані.
ALTER TABLE core.deadline_rules
ADD CONSTRAINT exclude_overlapping_rule_versions
EXCLUDE USING gist (
    code WITH =,
    daterange(valid_from, COALESCE(valid_to, 'infinity'::date), '[)') WITH &&
)
DEFERRABLE INITIALLY DEFERRED;

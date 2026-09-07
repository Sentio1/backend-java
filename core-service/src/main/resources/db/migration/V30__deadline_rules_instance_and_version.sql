-- ═══════════════════════════════════════════════════════════
-- Міграція: core.deadline_rules.instance, core.deadline_rules.version (SEN-24)
-- ═══════════════════════════════════════════════════════════
-- DeadlineRule.courtInstance/version на ентіті вже додані, тут наздоганяємо схему.
--
-- instance: той самий trigger_event_code (наприклад DECISION) означає різний строк і
-- іншу статтю ЦПК залежно від інстанції - право на апеляційне оскарження рішення першої
-- інстанції й право на касаційне оскарження постанови апеляційної це різні правила з
-- тим самим тригером. Тип - core.court_instance (уже є для courts, V24), той самий набір
-- значень, а не новий enum.
--
-- version: явний номер редакції правила поверх valid_from/valid_to (AC SEN-24) - щоб
-- юрист бачив "це третя редакція цього правила", а не лише діапазон дат.

ALTER TABLE core.deadline_rules
    ADD COLUMN instance core.court_instance NOT NULL DEFAULT 'FIRST',
    ADD COLUMN version  SMALLINT NOT NULL DEFAULT 1;

-- DEFAULT лише щоб ADD COLUMN NOT NULL не впав, якщо в таблиці вже є рядки (той самий
-- підхід, що й V25/V26) - на рівні застосунку обидва поля завжди обов'язкові й
-- обчислюються сервісом при створенні нової версії, тому дефолти одразу знімаємо.
ALTER TABLE core.deadline_rules
    ALTER COLUMN instance DROP DEFAULT,
    ALTER COLUMN version DROP DEFAULT;

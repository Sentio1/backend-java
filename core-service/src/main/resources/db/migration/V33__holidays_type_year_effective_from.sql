-- ═══════════════════════════════════════════════════════════
-- Міграція: core.holidays — holiday_type/year/effective_from (SEN-26)
-- ═══════════════════════════════════════════════════════════
-- holiday_type - єдине джерело правди про природу дня (PUBLIC_HOLIDAY /
-- TRANSFERRED_WORKING_DAY / TRANSFERRED_NON_WORKING_DAY). is_working (V9) прибираємо:
-- Holiday.isWorking() тепер похідний Java-метод (holidayType == TRANSFERRED_WORKING_DAY) -
-- синхронізація автоматична й безумовна, бо зберігається лише один факт, а не два поля,
-- які могли б розійтись.
--
-- year і effective_from - версійність: перенесення робочих днів в Україні оголошують
-- посеред року (постановою КМУ), і effective_from каже, відколи саме цей рядок календаря
-- чинний - це НЕ те саме, що версійність deadline_rules (valid_from/valid_to, стара справа
-- лишається на старій редакції): тут немає "старої редакції", яку треба зберегти для вже
-- порахованих дедлайнів - зміна календаря заднім числом не перераховує їх.

CREATE TYPE core.holiday_type AS ENUM (
    'PUBLIC_HOLIDAY',
    'TRANSFERRED_WORKING_DAY',
    'TRANSFERRED_NON_WORKING_DAY'
);

ALTER TABLE core.holidays
    ADD COLUMN holiday_type   core.holiday_type,
    ADD COLUMN year           SMALLINT,
    ADD COLUMN effective_from DATE;

-- Бекфіл на випадок уже наявних рядків (у жодному середовищі сідингу ще не було -
-- V9 без INSERT-ів, - але UPDATE безпечний і для порожньої таблиці). is_working=true
-- могло означати лише перенесену робочу суботу - зворотно розрізнити PUBLIC_HOLIDAY від
-- TRANSFERRED_NON_WORKING_DAY по самому лише is_working=false неможливо, тож бекфіл
-- консервативно трактує його як PUBLIC_HOLIDAY.
UPDATE core.holidays
SET holiday_type   = (CASE WHEN is_working THEN 'TRANSFERRED_WORKING_DAY' ELSE 'PUBLIC_HOLIDAY' END)::core.holiday_type,
    year            = EXTRACT(YEAR FROM date)::SMALLINT,
    effective_from = date
WHERE holiday_type IS NULL;

ALTER TABLE core.holidays
    ALTER COLUMN holiday_type SET NOT NULL,
    ALTER COLUMN year SET NOT NULL,
    ALTER COLUMN effective_from SET NOT NULL;

ALTER TABLE core.holidays
    DROP COLUMN is_working;

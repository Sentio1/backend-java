-- ═══════════════════════════════════════════════════════════
-- Міграція: core.courts.time_zone (SEN-19, Deadline Engine)
-- ═══════════════════════════════════════════════════════════
-- IANA zone id суду. Джерело зони для конвертації CaseEvent.occurred_at (Instant) у
-- календарну дату при розрахунку строків - суд, а не організація (courts живе в
-- тому самому core-service, без мережі до user-service; зона фізично належить
-- суду, а не юрфірмі). DEFAULT покриває всі вже засіяні суди - продукт поки що
-- суто український.
ALTER TABLE core.courts
    ADD COLUMN time_zone VARCHAR(50) NOT NULL DEFAULT 'Europe/Kyiv';

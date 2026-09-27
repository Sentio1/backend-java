-- ═══════════════════════════════════════════════════════════
-- Міграція: platform_role.SERVICE (Go-воркер service-to-service auth)
-- ═══════════════════════════════════════════════════════════
-- Окремий файл від V9, що сіє сам рядок service-юзера: Postgres не дозволяє
-- ALTER TYPE ... ADD VALUE і використання цього значення в одній транзакції,
-- а Flyway за замовчуванням гортає кожну міграцію в одній транзакції.
ALTER TYPE auth.platform_role ADD VALUE 'SERVICE';
ALTER TABLE auth.users DROP CONSTRAINT users_email_key;

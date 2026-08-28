-- ═══════════════════════════════════════════════════════════
-- Міграція: службовий User для Go-воркера Registry Monitor
-- ═══════════════════════════════════════════════════════════
-- Рядок у auth.users без organization_member (не належить жодній
-- організації - тому в його JWT не буде org_id) і з platform_role = SERVICE
-- (значення додане окремо в V8, зверху).
--
-- password_hash навмисно NULL тут: секрет сервісного акаунта не має жодного
-- шансу потрапити в git-історію, навіть хешованим. Реальний хеш виставляється
-- поза міграціями - або одноразовим SQL-запитом у кожному оточенні, або
-- стартовим кроком застосунку, що читає SERVICE_SECRET з Doppler/env і
-- (пере)хешує його в auth.users при зміні.
--
-- УВАГА для AuthService.serviceToken(): це НЕ той самий випадок, що null
-- password у AuthService.login() (`user.getPassword() != null && !matches(...)`,
-- тобто null -> перевірка пропускається -> логін проходить). Той ідіом писаний
-- під чисті OAuth-акаунти, де взагалі немає локального пароля і сам факт
-- "залогінився через Google" вже є доказом особи. Тут навпаки: NULL означає
-- "секрет ще не налаштований у цьому оточенні" і має ЗАБОРОНЯТИ видачу токена,
-- а не дозволяти будь-який пароль. Умову для serviceToken() пиши інвертовано:
-- `user.getPassword() == null || !passwordEncoder.matches(secret, hash)` -> throw.
INSERT INTO auth.users (id, email, password_hash, platform_role, created_at, updated_at)
VALUES (
    nextval('auth.user_seq_gen'),
    'registry-monitor@service.internal',
    NULL,
    'SERVICE',
    now(),
    now()
);

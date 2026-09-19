-- ═══════════════════════════════════════════════════════════
-- Міграція: сімейства refresh-токенів (rotation + reuse detection)
-- ═══════════════════════════════════════════════════════════
-- family_id: усі токени однієї сесії (один логін на одному пристрої) - при
-- ротації новий токен успадковує family_id старого. Повторне пред'явлення
-- вже ротованого токена = ознака крадіжки -> відкликається вся родина.
--
-- family_expires_at: абсолютна межа життя сесії від моменту логіну. Ротація
-- зсуває expires_at (sliding window), але ніколи не далі за цю межу.
--
-- revoke_reason: відрізняє "ротовано" від "вийшов/відкликано" - лише
-- повторне використання ROTATED-токена є reuse, а в межах короткого grace-
-- періоду після ротації це ще просто дві вкладки, що рефрешнулись одночасно.

CREATE TYPE auth.refresh_token_revoke_reason AS ENUM (
    'ROTATED',
    'LOGOUT',
    'SESSION_LIMIT',
    'REUSE_DETECTED',
    'ALL_SESSIONS_REVOKED'
);

ALTER TABLE auth.refresh_tokens
    ADD COLUMN family_id         UUID,
    ADD COLUMN family_expires_at TIMESTAMPTZ,
    ADD COLUMN revoke_reason     auth.refresh_token_revoke_reason;

-- Існуючі токени: кожен - окрема родина, межа = його власний expires_at.
UPDATE auth.refresh_tokens
SET family_id         = gen_random_uuid(),
    family_expires_at = expires_at;

UPDATE auth.refresh_tokens
SET revoke_reason = 'LOGOUT'
WHERE revoked_at IS NOT NULL;

ALTER TABLE auth.refresh_tokens
    ALTER COLUMN family_id SET NOT NULL,
    ALTER COLUMN family_expires_at SET NOT NULL;

CREATE INDEX refresh_tokens_family_id_idx
    ON auth.refresh_tokens (family_id);

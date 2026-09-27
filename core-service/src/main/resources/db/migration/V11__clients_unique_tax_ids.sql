-- ═══════════════════════════════════════════════════════════
-- Міграція: унікальність RNOKPP/EDRPOU в межах організації (SEN-17)
-- ═══════════════════════════════════════════════════════════

-- Частковий унікальний індекс: два активних клієнти однієї організації
-- не можуть мати однаковий РНОКПП/ЄДРПОУ. NULL і soft-deleted записи виключені,
-- тому це не заважає створювати клієнтів без цих полів і не блокує
-- повторне використання коду після soft-delete.
CREATE UNIQUE INDEX uq_clients_org_rnokpp
    ON core.clients (organization_id, rnokpp)
    WHERE deleted_at IS NULL AND rnokpp IS NOT NULL;

CREATE UNIQUE INDEX uq_clients_org_edrpou
    ON core.clients (organization_id, edrpou)
    WHERE deleted_at IS NULL AND edrpou IS NOT NULL;

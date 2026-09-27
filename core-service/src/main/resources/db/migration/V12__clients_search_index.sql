-- ═══════════════════════════════════════════════════════════
-- Міграція: індекс під реальний пошуковий вираз (SEN-17)
-- ═══════════════════════════════════════════════════════════

-- ClientRepository.searchClient шукає по конкатенації трьох полів, а не по голому
-- last_name - trgm-індекс з V2 під цей запит не підходить (Postgres використовує
-- лише індекс, що буквально збігається з виразом у WHERE) і зараз ніким не читається.
DROP INDEX IF EXISTS core.clients_last_name_trgm_idx;

CREATE INDEX clients_search_trgm_idx ON core.clients
    USING gin ((
        COALESCE(last_name, '') || ' ' || COALESCE(first_name, '') || ' ' || COALESCE(company_name, '')
    ) gin_trgm_ops);

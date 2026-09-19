-- ═══════════════════════════════════════════════════════════
-- Міграція: core.audit_logs (SEN-23)
-- ═══════════════════════════════════════════════════════════
-- Журнал змін по cases/case_events/deadlines: хто, коли, яке поле, старе й нове
-- значення. Одна спільна таблиця на всі три сутності (а не окрема історія на
-- кожну, як у V19__case_event_occurred_at_history.sql для occurred_at) - AC
-- вимагає уніфікованого журналу по трьох різних сутностях одразу.
--
-- Записи не редагуються й не видаляються (AC) - тому, на відміну від решти
-- таблиць цієї схеми, тут навмисно немає deleted_at/deleted_by/restored_*
-- (SoftDeleteEntity/CoreEntity), і немає updated_at (сутність - CreationTimestampedEntity,
-- не TimestampedEntity). "Не редагуються" не залишено самодисципліною рівня
-- застосунку (просто відсутній update-метод у репозиторії) - це легко порушити
-- випадково чи майбутнім кодом. BEFORE UPDATE/DELETE-тригер нижче гарантує це
-- на рівні БД незалежно від того, під якою роллю й яким шляхом іде запис - на
-- відміну від REVOKE, тригер спрацьовує і для суперюзера (локальний postgres-юзер
-- із docker-compose - суперюзер, для нього REVOKE був би фікцією).

CREATE SEQUENCE IF NOT EXISTS core.audit_log_seq_gen START WITH 1 INCREMENT BY 50;

CREATE TYPE core.audit_entity_type AS ENUM (
    'CASE',
    'CASE_EVENT',
    'DEADLINE'
);

CREATE TABLE core.audit_logs (
    id               BIGINT PRIMARY KEY,
    organization_id  BIGINT NOT NULL,                    -- soft-ref auth.organizations.id
    entity_type      core.audit_entity_type NOT NULL,
    entity_id        BIGINT NOT NULL,                     -- soft-ref core.cases/case_events/deadlines.id залежно від entity_type
    field_name       VARCHAR(100) NOT NULL,
    old_value        TEXT,
    new_value        TEXT,
    changed_by       BIGINT NOT NULL,                    -- soft-ref auth.users.id
    changed_at       TIMESTAMPTZ NOT NULL,                -- момент домену (передається викликом), може відрізнятись від created_at
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()   -- момент фізичного запису рядка
);

-- Основний патерн запиту: "уся історія для цього запису конкретної сутності в
-- межах організації" (картка справи -> історія по cases/case_events/deadlines).
CREATE INDEX audit_logs_organization_id_entity_type_entity_id_idx
    ON core.audit_logs (organization_id, entity_type, entity_id);

-- Хронологічна видача (найновіші зміни першими) в межах організації.
CREATE INDEX audit_logs_organization_id_changed_at_idx
    ON core.audit_logs (organization_id, changed_at);

CREATE OR REPLACE FUNCTION core.audit_logs_immutable() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'core.audit_logs is append-only: % is not allowed on id=%', TG_OP, OLD.id;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_logs_immutable
    BEFORE UPDATE OR DELETE ON core.audit_logs
    FOR EACH ROW EXECUTE FUNCTION core.audit_logs_immutable();

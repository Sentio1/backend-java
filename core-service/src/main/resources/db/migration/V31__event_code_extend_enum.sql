-- ═══════════════════════════════════════════════════════════
-- Міграція: core.event_code — додати відсутні значення (SEN-24)
-- ═══════════════════════════════════════════════════════════
-- V14 створив core.event_code лише з 'CLAIM_FILED'/'RULING_RECEIVED', але Java-enum
-- EventCode (case_event/enums/EventCode.java) з того часу вже має ще чотири значення -
-- DECISION/COPY_SERVED/HEARING/OTHER - які в DB-тип так ніколи й не потрапили. Досі це
-- нічим не проявлялось, бо жоден код і жодна міграція не намагались записати саме ці
-- чотири значення в колонку типу core.event_code (case_events.event_code,
-- deadline_rules.trigger_event_code) - V31 (сід базових правил ЦПК) став першим, хто
-- це зробив, і впав з "invalid input value for enum core.event_code".
--
-- Окремою міграцією (не разом із сідингом, який ці значення одразу використовує):
-- ALTER TYPE ... ADD VALUE не можна використати в тій самій транзакції/скрипті, де
-- значення додається.

ALTER TYPE core.event_code ADD VALUE IF NOT EXISTS 'DECISION';
ALTER TYPE core.event_code ADD VALUE IF NOT EXISTS 'COPY_SERVED';
ALTER TYPE core.event_code ADD VALUE IF NOT EXISTS 'HEARING';
ALTER TYPE core.event_code ADD VALUE IF NOT EXISTS 'OTHER';

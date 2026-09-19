-- ═══════════════════════════════════════════════════════════
-- Міграція: Розширення CHECK-обмежень для core.clients
-- ═══════════════════════════════════════════════════════════

-- 1. Видаляємо старий базовий CHECK імені
ALTER TABLE core.clients
    DROP CONSTRAINT IF EXISTS clients_type_name_check;

-- 2. Додаємо перевірку обов'язкової наявності адреси та контактів (NOT VALID для безпечного накатування на dev/staging)
ALTER TABLE core.clients
    ADD CONSTRAINT clients_address_check
        CHECK (address IS NOT NULL AND length(trim(address)) > 0) NOT VALID,
    ADD CONSTRAINT clients_contacts_check
        CHECK (
            (email IS NOT NULL AND length(trim(email)) > 0)
                OR (phone_number IS NOT NULL AND length(trim(phone_number)) > 0)
            ) NOT VALID;

-- 3. Додаємо вичерпний CHECK інваріантів за типами клієнтів (NOT VALID)
ALTER TABLE core.clients
    ADD CONSTRAINT clients_type_data_integrity_check
        CHECK (
            -- Фізична особа: ПІБ, дата народження, РНОКПП або паспорт
            (
                type = 'INDIVIDUAL'
                    AND last_name IS NOT NULL
                    AND first_name IS NOT NULL
                    AND birth_date IS NOT NULL
                    AND (rnokpp IS NOT NULL OR passport IS NOT NULL)
                )
                OR
                -- ФОП: ПІБ, дата народження, РНОКПП або ЄДРПОУ або паспорт
            (
                type = 'SOLE_TRADER'
                    AND last_name IS NOT NULL
                    AND first_name IS NOT NULL
                    AND birth_date IS NOT NULL
                    AND (rnokpp IS NOT NULL OR edrpou IS NOT NULL OR passport IS NOT NULL)
                )
                OR
                -- Юридична особа: назва, ЄДРПОУ, керівник, контактна особа
            (
                type = 'COMPANY'
                    AND company_name IS NOT NULL
                    AND edrpou IS NOT NULL
                    AND director_name IS NOT NULL
                    AND contact_person_name IS NOT NULL
                )
            ) NOT VALID;
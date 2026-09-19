-- ═══════════════════════════════════════════════════════════
-- Міграція: case_parties - процесуальні опоненти + розширення ролей (SEN-21)
-- ═══════════════════════════════════════════════════════════
-- Сторона у справі раніше завжди мусила бути клієнтом фірми (client_id NOT NULL). Але
-- процесуальний опонент - не клієнт фірми: заводити йому повноцінну картку Client
-- (SEN-20 вимагає адресу/РНОКПП-або-паспорт/дату народження/контакти) немає сенсу, бо
-- на момент внесення справи про опонента здебільшого відомо лише ім'я. Тому client_id
-- стає nullable, а опонент описується вільним текстом прямо на case_parties; рівно одне
-- з двох має бути заповнене - client_id (сторона - клієнт фірми) або opponent_name
-- (сторона - опонент). NOT VALID - той самий "безпечний накат" підхід, що й
-- V23__clients_extend_type_check_constraints.sql.

ALTER TABLE core.case_parties ALTER COLUMN client_id DROP NOT NULL;

ALTER TABLE core.case_parties
    ADD COLUMN opponent_name    TEXT,
    ADD COLUMN opponent_contact TEXT,
    ADD COLUMN opponent_details TEXT;

ALTER TABLE core.case_parties
    ADD CONSTRAINT case_parties_client_xor_opponent_check
        CHECK ((client_id IS NOT NULL) != (opponent_name IS NOT NULL)) NOT VALID;

-- ── role: розширений набір під усі види судочинства з AC (цивільне/господарське/
-- адміністративне/кримінальне) - PLAINTIFF/DEFENDANT/THIRD_PARTY замало для
-- адміністративного (заявник/заінтересована особа) чи кримінального (потерпілий/
-- підозрюваний) провадження. REPRESENTATIVE/OTHER прибрані як завузький/розмитий випадок.
ALTER TYPE core.case_party_role RENAME TO case_party_role_old;

CREATE TYPE core.case_party_role AS ENUM (
    'PLAINTIFF',
    'DEFENDANT',
    'THIRD_PARTY',
    'APPLICANT',
    'INTERESTED_PERSON',
    'VICTIM',
    'SUSPECT',
    'OPPONENT'
);

-- REPRESENTATIVE/OTHER даних ще ніде немає - мапінг нижче лише страховка на випадок
-- ручного сідингу, не реальна міграція даних.
ALTER TABLE core.case_parties
    ALTER COLUMN role TYPE core.case_party_role
    USING (
        CASE role::text
            WHEN 'REPRESENTATIVE' THEN 'OPPONENT'
            WHEN 'OTHER' THEN 'OPPONENT'
            ELSE role::text
        END
    )::core.case_party_role;

DROP TYPE core.case_party_role_old;

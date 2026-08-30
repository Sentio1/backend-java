-- ═══════════════════════════════════════════════════════════
-- Міграція: core.case_events.registry_document_text_ref (SEN-69)
-- ═══════════════════════════════════════════════════════════
-- Посилання на повний текст документа в Mongo (Registry Monitor) - без нього
-- SEN-69 ("Список знайдених документів з реєстру, з можливістю прочитати повний
-- текст") неможливо реалізувати без повторного походу до Registry Monitor за
-- кожним документом. Nullable: заповнене лише для source = REGISTRY, для MANUAL
-- завжди NULL.
ALTER TABLE core.case_events ADD COLUMN registry_document_text_ref VARCHAR(500);

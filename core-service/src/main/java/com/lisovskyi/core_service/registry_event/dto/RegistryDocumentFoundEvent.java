package com.lisovskyi.core_service.registry_event.dto;

import java.time.Instant;

public record RegistryDocumentFoundEvent(
        Long registryDocId,
        Long caseId,
        Long orgId,
        String type,
        Instant occurredAt,
        String court,
        String documentTextRef
) {}

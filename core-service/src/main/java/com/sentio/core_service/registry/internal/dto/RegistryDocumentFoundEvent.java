package com.sentio.core_service.registry.internal.dto;

import java.time.Instant;

public record RegistryDocumentFoundEvent(
        Long registryDocId,
        Long caseId,
        Long orgId,
        String type,
        Instant occurredAt,
        String court,
        String documentTextRef) {}

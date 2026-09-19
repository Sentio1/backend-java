package com.sentio.core_service.client.api.spi;

/**
 * Implemented by the module that owns cases (litigation). The client module can't ask about cases
 * itself - litigation already depends on client (case parties reference clients), so calling back
 * would form a cycle. Instead client declares what it needs and litigation provides it.
 */
public interface ClientActiveCasesChecker {

    /** True if the client is a party to at least one case that isn't in a terminal status. */
    boolean hasActiveCases(long clientId, long organizationId);
}

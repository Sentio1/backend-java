package com.lisovskyi.core_service.registry_event;

public final class RegistryEventConstants {

    private RegistryEventConstants() {
        throw new UnsupportedOperationException();
    }

    public static final String DOCUMENT_FOUND_TOPIC = "registry.document-found";

    public static final int MAX_RETRY_ATTEMPTS = 3;
    public static final long RETRY_BACKOFF_MS = 1000L;
}

package com.sentio.core_service.client.internal.exception;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;

public class ClientNotFoundException extends ResourceNotFoundException {

    public ClientNotFoundException(long clientId) {
        super("Client", "id", clientId);
    }
}

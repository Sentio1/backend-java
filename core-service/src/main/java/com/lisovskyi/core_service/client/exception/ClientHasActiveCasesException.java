package com.lisovskyi.core_service.client.exception;

import com.lisovskyi.web.error.autoconfigure.base.AppException;
import com.sentio.shared.entity.id.client.ClientId;
import org.springframework.http.HttpStatus;

public class ClientHasActiveCasesException extends AppException {

    public ClientHasActiveCasesException(ClientId clientId) {
        super(
                "Client " + clientId + " has active cases and cannot be deleted",
                HttpStatus.CONFLICT,
                "CLIENT_HAS_ACTIVE_CASES");
    }
}

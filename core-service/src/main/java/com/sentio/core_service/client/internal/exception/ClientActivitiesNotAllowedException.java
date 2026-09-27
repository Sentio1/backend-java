package com.sentio.core_service.client.internal.exception;

import com.lisovskyi.web.error.autoconfigure.base.AppException;
import org.springframework.http.HttpStatus;

/** Activities (КВЕДи) belong only to sole traders - not to individuals or companies. */
public class ClientActivitiesNotAllowedException extends AppException {

    public ClientActivitiesNotAllowedException() {
        super("Activities can only be modified for SOLE_TRADER clients",
                HttpStatus.BAD_REQUEST, "CLIENT_ACTIVITIES_NOT_ALLOWED");
    }
}

package com.sentio.user_service.identity.organization.api.exception;

import com.lisovskyi.web.error.autoconfigure.base.AppException;
import org.springframework.http.HttpStatus;

/**
 * The operation would leave an organization without any OWNER (role change, member removal,
 * account deletion). 409: the request is well-formed, it conflicts with the organization's current
 * state - promoting someone else to OWNER first resolves it. In api/ because the user module throws
 * it too (deleting the account of a last owner).
 */
public class LastOwnerException extends AppException {

    public static final String CODE = "LAST_OWNER";

    public LastOwnerException(String message) {
        super(message, HttpStatus.CONFLICT, CODE);
    }
}

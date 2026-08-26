package com.sentio.shared.persistence;

import java.sql.SQLException;
import java.util.Arrays;
import org.springframework.dao.DataIntegrityViolationException;

public final class ConstraintViolations {

    private ConstraintViolations() {
        throw new UnsupportedOperationException();
    }

    public static boolean isUniqueConstraintViolation(DataIntegrityViolationException e, String... constraintNames) {
        Throwable root = e.getRootCause();
        if (!(root instanceof SQLException sqlEx)) return false;
        String message = sqlEx.getMessage();
        if (message == null) return false;
        return Arrays.stream(constraintNames).anyMatch(message::contains);
    }
}

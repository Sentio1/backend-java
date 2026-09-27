package com.sentio.user_service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lisovskyi.web.error.autoconfigure.base.AppException;
import com.sentio.user_service.identity.organization.api.exception.LastOwnerException;
import com.sentio.user_service.identity.user.internal.exception.LastPlatformAdminException;
import com.sentio.user_service.identity.user.internal.exception.UserNotDeletedException;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

/**
 * The web-error starter maps every AppException generically (status + code from the exception),
 * so the HTTP contract of a business-rule refusal is exactly what the exception declares. Pinned
 * here: the frontend reacts to these codes.
 */
class DomainExceptionsTest {

    static Stream<Arguments> exceptions() {
        return Stream.of(
                Arguments.of(new LastOwnerException("x"), HttpStatus.CONFLICT, "LAST_OWNER"),
                Arguments.of(new LastPlatformAdminException(1L), HttpStatus.CONFLICT, "LAST_PLATFORM_ADMIN"),
                Arguments.of(new UserNotDeletedException(1L), HttpStatus.CONFLICT, "USER_NOT_DELETED"));
    }

    @ParameterizedTest
    @MethodSource("exceptions")
    void mapsToItsOwnStatusAndCode(AppException exception, HttpStatus status, String code) {
        assertThat(exception.getStatus()).isEqualTo(status);
        assertThat(exception.getCode()).isEqualTo(code);
    }
}

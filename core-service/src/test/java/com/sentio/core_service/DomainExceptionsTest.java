package com.sentio.core_service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lisovskyi.web.error.autoconfigure.base.AppException;
import com.sentio.core_service.client.internal.exception.ClientActivitiesNotAllowedException;
import com.sentio.core_service.deadline.internal.exception.DeadlineRuleValidFromConflictException;
import java.time.LocalDate;
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
                Arguments.of(new ClientActivitiesNotAllowedException(),
                        HttpStatus.BAD_REQUEST, "CLIENT_ACTIVITIES_NOT_ALLOWED"),
                Arguments.of(new DeadlineRuleValidFromConflictException(
                                "CPC_X", LocalDate.of(2024, 1, 1), LocalDate.of(2023, 1, 1)),
                        HttpStatus.CONFLICT, "DEADLINE_RULE_VALID_FROM_CONFLICT"));
    }

    @ParameterizedTest
    @MethodSource("exceptions")
    void mapsToItsOwnStatusAndCode(AppException exception, HttpStatus status, String code) {
        assertThat(exception.getStatus()).isEqualTo(status);
        assertThat(exception.getCode()).isEqualTo(code);
    }
}

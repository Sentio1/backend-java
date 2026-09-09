package com.lisovskyi.core_service.case_.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.lisovskyi.core_service.case_.enums.CaseInstance;
import com.lisovskyi.core_service.case_.enums.CaseStatus;
import com.lisovskyi.core_service.case_.enums.ProcedureType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;

/**
 * SEN-21 review fix: pins down the "must not be explicitly null" guards on {@link
 * CaseUpdateRequest} for the fields NOT NULL on {@code Case} (title/procedure/instance/status/
 * responsibleUserId/registryWatchEnabled). Runs through real Bean Validation, not the boolean
 * methods directly - the point is that an explicit {@code "title": null} in a PATCH body now
 * fails at the controller boundary (400) instead of an NPE or a Hibernate not-null flush error
 * (500) deeper in CaseMapper.applyPresentFields.
 */
class CaseUpdateRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    private CaseUpdateRequest allUndefinedRequest() {
        return new CaseUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined());
    }

    @Test
    void allFieldsAbsent_isValid() {
        assertThat(validator.validate(allUndefinedRequest())).isEmpty();
    }

    @Test
    void allFieldsPresentWithValues_isValid() {
        CaseUpdateRequest request = new CaseUpdateRequest(
                JsonNullable.of("761/1234/25"),
                JsonNullable.of("Новий позов"),
                JsonNullable.undefined(),
                JsonNullable.of(ProcedureType.COMMERCIAL),
                JsonNullable.of(CaseInstance.APPEAL),
                JsonNullable.of(CaseStatus.SUSPENDED),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.of(99L),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.of(true));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void courtIdExplicitlyNull_isValid_becauseCourtIsNullableOnTheEntity() {
        CaseUpdateRequest request = new CaseUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.of(null),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined());

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void titleExplicitlyNull_isRejected() {
        CaseUpdateRequest request = new CaseUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.of(null),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined());

        Set<ConstraintViolation<CaseUpdateRequest>> violations = validator.validate(request);

        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .contains("'title' must not be explicitly set to null");
    }

    @Test
    void procedureExplicitlyNull_isRejected() {
        CaseUpdateRequest request = new CaseUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.of(null),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined());

        assertThat(validator.validate(request))
                .extracting(ConstraintViolation::getMessage)
                .contains("'procedure' must not be explicitly set to null");
    }

    @Test
    void instanceExplicitlyNull_isRejected() {
        CaseUpdateRequest request = new CaseUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.of(null),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined());

        assertThat(validator.validate(request))
                .extracting(ConstraintViolation::getMessage)
                .contains("'instance' must not be explicitly set to null");
    }

    @Test
    void statusExplicitlyNull_isRejected() {
        CaseUpdateRequest request = new CaseUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.of(null),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined());

        assertThat(validator.validate(request))
                .extracting(ConstraintViolation::getMessage)
                .contains("'status' must not be explicitly set to null");
    }

    @Test
    void responsibleUserIdExplicitlyNull_isRejected() {
        CaseUpdateRequest request = new CaseUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.of(null),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined());

        assertThat(validator.validate(request))
                .extracting(ConstraintViolation::getMessage)
                .contains("'responsibleUserId' must not be explicitly set to null");
    }

    @Test
    void registryWatchEnabledExplicitlyNull_isRejected() {
        CaseUpdateRequest request = new CaseUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.of(null));

        assertThat(validator.validate(request))
                .extracting(ConstraintViolation::getMessage)
                .contains("'registryWatchEnabled' must not be explicitly set to null");
    }
}

package com.lisovskyi.core_service.case_party.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.lisovskyi.core_service.case_party.CasePartyRole;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;

// SEN-21 review fix: той самий "must not be explicitly null" guard, що й CaseUpdateRequestValidationTest,
// для role (NOT NULL) та isPrimary (примітивний boolean на CaseParty).
class CasePartyUpdateRequestValidationTest {

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

    private CasePartyUpdateRequest allUndefinedRequest() {
        return new CasePartyUpdateRequest(
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
        CasePartyUpdateRequest request = new CasePartyUpdateRequest(
                JsonNullable.of(5L),
                JsonNullable.of(CasePartyRole.DEFENDANT),
                JsonNullable.of(true),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined());

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void clientIdExplicitlyNull_isValid_becauseItMeansConvertingToAnOpponent() {
        CasePartyUpdateRequest request = new CasePartyUpdateRequest(
                JsonNullable.of(null),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined());

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void roleExplicitlyNull_isRejected() {
        CasePartyUpdateRequest request = new CasePartyUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.of(null),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined());

        assertThat(validator.validate(request))
                .extracting(ConstraintViolation::getMessage)
                .contains("'role' must not be explicitly set to null");
    }

    @Test
    void isPrimaryExplicitlyNull_isRejected() {
        CasePartyUpdateRequest request = new CasePartyUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.of(null),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined());

        assertThat(validator.validate(request))
                .extracting(ConstraintViolation::getMessage)
                .contains("'isPrimary' must not be explicitly set to null");
    }
}

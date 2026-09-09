package com.lisovskyi.core_service.deadline_rule.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;

/**
 * Pins down the "must not be explicitly null" guards on {@link DeadlineRuleUpdateRequest} for
 * title/legalBasis/extendable (all NOT NULL on {@code DeadlineRule}) - same pattern as
 * CaseUpdateRequestValidationTest. validTo is deliberately NOT guarded: it's genuinely nullable
 * on the entity ({@code null} = "чинне безстроково"), so an explicit {@code "validTo": null} is a
 * legitimate way to reopen a closed rule, not a client mistake.
 */
class DeadlineRuleUpdateRequestValidationTest {

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

    private DeadlineRuleUpdateRequest allUndefinedRequest() {
        return new DeadlineRuleUpdateRequest(
                1L, JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined());
    }

    @Test
    void allFieldsAbsent_isValid() {
        assertThat(validator.validate(allUndefinedRequest())).isEmpty();
    }

    @Test
    void allFieldsPresentWithValues_isValid() {
        DeadlineRuleUpdateRequest request = new DeadlineRuleUpdateRequest(
                1L,
                JsonNullable.of("Уточнена назва"),
                JsonNullable.of("ст. 179 ЦПК України"),
                JsonNullable.of(false),
                JsonNullable.of(LocalDate.of(2026, 1, 1)));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void nullId_isRejected() {
        DeadlineRuleUpdateRequest request = new DeadlineRuleUpdateRequest(
                null, JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined());

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void validToExplicitlyNull_isValid_becauseValidToIsNullableOnTheEntity() {
        DeadlineRuleUpdateRequest request = new DeadlineRuleUpdateRequest(
                1L, JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.of(null));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void titleExplicitlyNull_isRejected() {
        DeadlineRuleUpdateRequest request = new DeadlineRuleUpdateRequest(
                1L, JsonNullable.of(null), JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined());

        Set<ConstraintViolation<DeadlineRuleUpdateRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("'title' must not be explicitly set to null");
    }

    @Test
    void legalBasisExplicitlyNull_isRejected() {
        DeadlineRuleUpdateRequest request = new DeadlineRuleUpdateRequest(
                1L, JsonNullable.undefined(), JsonNullable.of(null), JsonNullable.undefined(), JsonNullable.undefined());

        assertThat(validator.validate(request))
                .extracting(ConstraintViolation::getMessage)
                .contains("'legalBasis' must not be explicitly set to null");
    }

    @Test
    void extendableExplicitlyNull_isRejected() {
        DeadlineRuleUpdateRequest request = new DeadlineRuleUpdateRequest(
                1L, JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.of(null), JsonNullable.undefined());

        assertThat(validator.validate(request))
                .extracting(ConstraintViolation::getMessage)
                .contains("'extendable' must not be explicitly set to null");
    }

    @Test
    void blankTitle_isNormalizedToNull_andThenRejectedByTheGuard() {
        // StringNormalization.blankToNull у компактному конструкторі перетворює "   " на null
        // ЩЕ ДО валідації - тож порожній рядок ловить та сама "must not be explicitly null"
        // перевірка, а не окреме @NotBlank/@Size повідомлення.
        DeadlineRuleUpdateRequest request = new DeadlineRuleUpdateRequest(
                1L, JsonNullable.of("   "), JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined());

        assertThat(validator.validate(request))
                .extracting(ConstraintViolation::getMessage)
                .contains("'title' must not be explicitly set to null");
    }
}

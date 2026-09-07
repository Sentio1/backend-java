package com.lisovskyi.core_service.holiday.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.lisovskyi.core_service.holiday.enums.HolidayType;
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
 * Pins down the "must not be explicitly null" guards on {@link HolidayUpdateRequest} for
 * name/holidayType/effectiveFrom (all NOT NULL on {@code Holiday}) - same pattern as
 * DeadlineRuleUpdateRequestValidationTest. Unlike that one, there's no exempt nullable field here
 * (validTo on DeadlineRule is genuinely nullable; every patchable field on Holiday is NOT NULL).
 */
class HolidayUpdateRequestValidationTest {

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

    private HolidayUpdateRequest allUndefinedRequest() {
        return new HolidayUpdateRequest(
                LocalDate.of(2026, 1, 1), JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined());
    }

    @Test
    void allFieldsAbsent_isValid() {
        assertThat(validator.validate(allUndefinedRequest())).isEmpty();
    }

    @Test
    void allFieldsPresentWithValues_isValid() {
        HolidayUpdateRequest request = new HolidayUpdateRequest(
                LocalDate.of(2026, 1, 1),
                JsonNullable.of("Уточнена назва"),
                JsonNullable.of(HolidayType.TRANSFERRED_WORKING_DAY),
                JsonNullable.of(LocalDate.of(2026, 6, 1)));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void nullDate_isRejected() {
        HolidayUpdateRequest request = new HolidayUpdateRequest(
                null, JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined());

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void nameExplicitlyNull_isRejected() {
        HolidayUpdateRequest request = new HolidayUpdateRequest(
                LocalDate.of(2026, 1, 1), JsonNullable.of(null), JsonNullable.undefined(), JsonNullable.undefined());

        Set<ConstraintViolation<HolidayUpdateRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("'name' must not be explicitly set to null");
    }

    @Test
    void holidayTypeExplicitlyNull_isRejected() {
        HolidayUpdateRequest request = new HolidayUpdateRequest(
                LocalDate.of(2026, 1, 1), JsonNullable.undefined(), JsonNullable.of(null), JsonNullable.undefined());

        assertThat(validator.validate(request))
                .extracting(ConstraintViolation::getMessage)
                .contains("'holidayType' must not be explicitly set to null");
    }

    @Test
    void effectiveFromExplicitlyNull_isRejected() {
        HolidayUpdateRequest request = new HolidayUpdateRequest(
                LocalDate.of(2026, 1, 1), JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.of(null));

        assertThat(validator.validate(request))
                .extracting(ConstraintViolation::getMessage)
                .contains("'effectiveFrom' must not be explicitly set to null");
    }

    @Test
    void blankName_isNormalizedToNull_andThenRejectedByTheGuard() {
        // StringNormalization.blankToNull у компактному конструкторі перетворює "   " на null
        // ЩЕ ДО валідації - тож порожній рядок ловить та сама "must not be explicitly null"
        // перевірка, а не окреме @NotBlank/@Size повідомлення.
        HolidayUpdateRequest request = new HolidayUpdateRequest(
                LocalDate.of(2026, 1, 1), JsonNullable.of("   "), JsonNullable.undefined(), JsonNullable.undefined());

        assertThat(validator.validate(request))
                .extracting(ConstraintViolation::getMessage)
                .contains("'name' must not be explicitly set to null");
    }
}

package com.lisovskyi.core_service.holiday.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.lisovskyi.core_service.holiday.enums.HolidayType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * SEN-26: holidayType is deliberately required (@NotNull, no silent default) - same reasoning as
 * DeadlineRuleCreateRequestValidationTest's durationUnit/dayKind/countFrom/extendable (AC SEN-24):
 * a calendar row's whole point is recording what kind of day this is, so the author states it
 * explicitly rather than falling back to a hidden default. year is deliberately absent from the
 * record entirely (derived from date server-side), so there's nothing to validate for it here.
 */
class HolidayCreateRequestValidationTest {

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

    private static final class Req {
        private LocalDate date = LocalDate.of(2026, 1, 1);
        private String name = "Новий рік";
        private HolidayType holidayType = HolidayType.PUBLIC_HOLIDAY;
        private LocalDate effectiveFrom = LocalDate.of(2026, 1, 1);

        HolidayCreateRequest build() {
            return new HolidayCreateRequest(date, name, holidayType, effectiveFrom);
        }
    }

    @Test
    void fullyPopulatedRequest_isValid() {
        assertThat(validator.validate(new Req().build())).isEmpty();
    }

    @Test
    void nullDate_isRejected() {
        Req req = new Req();
        req.date = null;

        assertThat(validator.validate(req.build())).isNotEmpty();
    }

    @Test
    void blankName_isRejected() {
        Req req = new Req();
        req.name = "   ";

        assertThat(validator.validate(req.build())).isNotEmpty();
    }

    @Test
    void nameTooLong_isRejected() {
        Req req = new Req();
        req.name = "X".repeat(101);

        assertThat(validator.validate(req.build())).isNotEmpty();
    }

    @Test
    void nullHolidayType_isRejected_noSilentDefault() {
        Req req = new Req();
        req.holidayType = null;

        assertThat(validator.validate(req.build())).isNotEmpty();
    }

    @Test
    void nullEffectiveFrom_isRejected() {
        Req req = new Req();
        req.effectiveFrom = null;

        assertThat(validator.validate(req.build())).isNotEmpty();
    }

    @Test
    void effectiveFromInTheFuture_isValid_becauseTransfersCanBeAnnouncedInAdvance() {
        Req req = new Req();
        req.effectiveFrom = LocalDate.of(2027, 1, 1);

        assertThat(validator.validate(req.build())).isEmpty();
    }
}

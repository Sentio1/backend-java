package com.lisovskyi.core_service.client.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.Test;

/**
 * SEN-17 review fix: the weight set must be picked from the code's own range (below/at-or-above
 * 30_000_000), not as a fallback tried only when the first attempt's remainder is 10. {@code
 * 32855961} is the coderabbit-flagged regression case - a real ЄДРПОУ from the Ministry of
 * Economic Development reference, taken from https://me.gov.ua/file/713bfc5e-1214-4ff0-9082-50e46f054474.
 */
class EdrpouValidatorTest {

    private final EdrpouValidator validator = new EdrpouValidator();

    // Never dereferenced by the implementation - isValid() doesn't build custom violations.
    private final ConstraintValidatorContext context = null;

    @Test
    void acceptsRealEdrpouAboveRangeThreshold_regressionFor32855961() {
        // 32855961 >= 30_000_000, so the correct weight set is WEIGHTS_2 upfront. The old
        // implementation always tried WEIGHTS_1 first, got remainder 7 (not 10, so no
        // fallback fired), and wrongly rejected this real code.
        assertThat(validator.isValid("32855961", context)).isTrue();
    }

    @Test
    void acceptsValidEdrpouBelowRangeThreshold() {
        // 14943366 < 30_000_000 -> WEIGHTS_1. sum = 1*1+4*2+9*3+4*4+3*5+3*6+6*7 = 127,
        // 127 % 11 = 6, matches the 8th digit.
        assertThat(validator.isValid("14943366", context)).isTrue();
    }

    @Test
    void rejectsWrongCheckDigit_belowRangeThreshold() {
        // Same base digits as above, wrong check digit (0 instead of the correct 6).
        assertThat(validator.isValid("14943360", context)).isFalse();
    }

    @Test
    void rejectsWrongCheckDigit_aboveRangeThreshold() {
        assertThat(validator.isValid("32855960", context)).isFalse();
    }

    /**
     * SEN-20 review fix: on a remainder of 10, the correct algorithm recomputes with the *same*
     * range's weights shifted by +2 (WEIGHTS_1 -> WEIGHTS_1_FALLBACK), not by switching to the
     * other range's base weights. All four codes below were constructed to actually hit a
     * remainder of 10 on the first pass, cross-checked against a from-scratch re-implementation of
     * the corrected algorithm - and confirmed to be rejected by the pre-fix logic (which swapped
     * WEIGHTS_1 <-> WEIGHTS_2 instead of adding 2), making them real regression cases, not just
     * plausible-looking numbers.
     */
    @Test
    void acceptsValidEdrpou_belowRangeThreshold_whenFirstPassRemainderIsTen() {
        assertThat(validator.isValid("10000062", context)).isTrue();
    }

    @Test
    void acceptsValidEdrpou_withinMidRange_whenFirstPassRemainderIsTen() {
        assertThat(validator.isValid("31000093", context)).isTrue();
    }

    /**
     * SEN-20 review fix: the old range check was {@code < 30_000_000 ? WEIGHTS_1 : WEIGHTS_2} with
     * no upper bound, so every code above 60_000_000 was wrongly validated with WEIGHTS_2 instead
     * of falling back to WEIGHTS_1. Real EDRPOU codes go up to 99_999_999, so this rejected a wide
     * swath of otherwise-valid codes.
     */
    @Test
    void acceptsValidEdrpou_aboveMidRangeUpperBound() {
        assertThat(validator.isValid("61000014", context)).isTrue();
    }

    @Test
    void acceptsValidEdrpou_aboveMidRangeUpperBound_whenFirstPassRemainderIsTen() {
        assertThat(validator.isValid("61000051", context)).isTrue();
    }

    @Test
    void rejectsNonEightDigitValues() {
        assertThat(validator.isValid("1234567", context)).isFalse();
        assertThat(validator.isValid("123456789", context)).isFalse();
        assertThat(validator.isValid("1234567a", context)).isFalse();
    }

    @Test
    void treatsNullAndBlankAsValid_presenceIsEnforcedElsewhere() {
        assertThat(validator.isValid(null, context)).isTrue();
        assertThat(validator.isValid("", context)).isTrue();
        assertThat(validator.isValid("   ", context)).isTrue();
    }
}

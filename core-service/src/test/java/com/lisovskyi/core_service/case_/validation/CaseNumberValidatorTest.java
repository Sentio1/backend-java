package com.lisovskyi.core_service.case_.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.Test;

// SEN-21 AC: "Номер справи у форматі ЄДРСР (XXX/XXXX/XX) з валідацією".
class CaseNumberValidatorTest {

    private final CaseNumberValidator validator = new CaseNumberValidator();

    // Never dereferenced by the implementation - isValid() doesn't build custom violations.
    private final ConstraintValidatorContext context = null;

    @Test
    void acceptsTypicalEdrsrFormat() {
        assertThat(validator.isValid("761/1234/25", context)).isTrue();
    }

    @Test
    void acceptsMinimalDigitCounts() {
        assertThat(validator.isValid("1/1/00", context)).isTrue();
    }

    @Test
    void acceptsMaximalDigitCounts() {
        assertThat(validator.isValid("12345/12345678/99", context)).isTrue();
    }

    @Test
    void acceptsSurroundingWhitespace_becauseValidatorStripsBeforeMatching() {
        assertThat(validator.isValid("  761/1234/25  ", context)).isTrue();
    }

    @Test
    void rejectsWrongSeparators() {
        assertThat(validator.isValid("761-1234-25", context)).isFalse();
    }

    @Test
    void rejectsFourDigitYear() {
        assertThat(validator.isValid("761/1234/2025", context)).isFalse();
    }

    @Test
    void rejectsNonNumericSegments() {
        assertThat(validator.isValid("abc/1234/25", context)).isFalse();
    }

    @Test
    void rejectsMissingSegment() {
        assertThat(validator.isValid("761/1234", context)).isFalse();
    }

    @Test
    void rejectsTooManyDigitsInFirstSegment() {
        assertThat(validator.isValid("123456/1234/25", context)).isFalse();
    }

    @Test
    void treatsNullAndBlankAsValid_presenceIsEnforcedElsewhere() {
        assertThat(validator.isValid(null, context)).isTrue();
        assertThat(validator.isValid("", context)).isTrue();
        assertThat(validator.isValid("   ", context)).isTrue();
    }
}

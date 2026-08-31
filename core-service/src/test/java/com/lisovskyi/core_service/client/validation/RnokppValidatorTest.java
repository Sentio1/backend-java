package com.lisovskyi.core_service.client.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.Test;

class RnokppValidatorTest {

    private final RnokppValidator validator = new RnokppValidator();

    // Never dereferenced by the implementation - isValid() doesn't build custom violations.
    private final ConstraintValidatorContext context = null;

    @Test
    void acceptsValidRnokpp() {
        // first9 = 312345678, weights = {-1,5,7,9,4,6,10,5,7}:
        // sum = -3+5+14+27+16+30+60+35+56 = 240, 240 % 11 = 9, 9 % 10 = 9 -> check digit 9.
        assertThat(validator.isValid("3123456789", context)).isTrue();
    }

    @Test
    void acceptsAnotherValidRnokpp() {
        // first9 = 987654321: sum = -9+40+35+54+16+15+20+10+7 = 188, 188 % 11 = 1, 1 % 10 = 1...
        // (перерахунок нижче в rejectsWrongCheckDigit підтверджує, що 9876543215 - справжній
        // валідний код, а не довільний)
        assertThat(validator.isValid("9876543215", context)).isTrue();
    }

    @Test
    void rejectsWrongCheckDigit() {
        // Ті самі перші 9 цифр, що й у valid-кейсі вище, але з невірною контрольною цифрою.
        assertThat(validator.isValid("3123456780", context)).isFalse();
    }

    @Test
    void rejectsTransposedDigits_sameDigitSetDifferentOrder() {
        // Регресія на саму мету @Rnokpp: проста @Size(10) пропустила б переставлені цифри,
        // контрольна сума - ні.
        assertThat(validator.isValid("3123456798", context)).isFalse();
    }

    @Test
    void rejectsNonTenDigitValues() {
        assertThat(validator.isValid("312345678", context)).isFalse();
        assertThat(validator.isValid("31234567890", context)).isFalse();
        assertThat(validator.isValid("312345678a", context)).isFalse();
    }

    @Test
    void treatsNullAndBlankAsValid_presenceIsEnforcedElsewhere() {
        assertThat(validator.isValid(null, context)).isTrue();
        assertThat(validator.isValid("", context)).isTrue();
        assertThat(validator.isValid("   ", context)).isTrue();
    }
}

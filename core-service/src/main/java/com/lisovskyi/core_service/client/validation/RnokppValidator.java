package com.lisovskyi.core_service.client.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Official РНОКПП check-digit algorithm: digits 1-9 are weighted, summed, reduced mod 11 then
 * mod 10, and the result must equal digit 10.
 */
public class RnokppValidator implements ConstraintValidator<Rnokpp, String> {

    private static final int[] WEIGHTS = {-1, 5, 7, 9, 4, 6, 10, 5, 7};

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        if (!value.matches("\\d{10}")) {
            return false;
        }

        int sum = 0;
        for (int i = 0; i < WEIGHTS.length; i++) {
            sum += (value.charAt(i) - '0') * WEIGHTS[i];
        }

        // Java's % keeps the dividend's sign, and sum can go negative because of the -1
        // weight - normalise to a true mathematical mod before the second reduction.
        int mod11 = ((sum % 11) + 11) % 11;
        int checkDigit = mod11 % 10;

        return checkDigit == (value.charAt(9) - '0');
    }

    public static boolean isValid(String value) {
        return new RnokppValidator().isValid(value, null);
    }
}

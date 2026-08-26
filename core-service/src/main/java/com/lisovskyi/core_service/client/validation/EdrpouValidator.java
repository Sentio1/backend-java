package com.lisovskyi.core_service.client.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Official ЄДРПОУ check-digit algorithm: digits 1-7 are weighted and summed mod 11. If that
 * reduction lands on 10 (not a valid single digit), it's recomputed with a second weight set;
 * if it's still 10, the check digit is defined as 0.
 */
public class EdrpouValidator implements ConstraintValidator<Edrpou, String> {

    private static final int[] WEIGHTS_1 = {1, 2, 3, 4, 5, 6, 7};
    private static final int[] WEIGHTS_2 = {7, 1, 2, 3, 4, 5, 6};

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        if (!value.matches("\\d{8}")) {
            return false;
        }

        int[] digits = value.chars().map(c -> c - '0').toArray();

        int checkDigit = weightedMod11(digits, WEIGHTS_1);
        if (checkDigit == 10) {
            checkDigit = weightedMod11(digits, WEIGHTS_2);
        }
        if (checkDigit == 10) {
            checkDigit = 0;
        }

        return checkDigit == digits[7];
    }

    private static int weightedMod11(int[] digits, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += digits[i] * weights[i];
        }
        return sum % 11;
    }
}

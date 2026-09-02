package com.lisovskyi.core_service.client.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Official ЄДРПОУ check-digit algorithm: digits 1-7 are weighted and summed, then reduced mod 11.
 * The weight set is picked from the code's own range - codes below 30_000_000 or above
 * 60_000_000 use {@link #WEIGHTS_1}, codes in the inclusive [30_000_000, 60_000_000] range use
 * {@link #WEIGHTS_2} - matching the reference implementation (cross-checked against multiple
 * independent EDRPOU validators: the Ministry of Economic Development algorithm description,
 * <a href="https://github.com/alazurenko/validate-edrpou">...</a>, and <a href="https://gist.github.com/ap-Codkelden">...</a>).
 *
 * <p>If that first attempt's remainder is 10 (not a valid single digit), it's recomputed with the
 * *same* range's weights shifted by +2 ({@link #WEIGHTS_1_FALLBACK} / {@link
 * #WEIGHTS_2_FALLBACK}) - not by switching to the other range's base weights, which was this
 * class's original bug (SEN-20 review fix): it swapped WEIGHTS_1 <-> WEIGHTS_2 on a remainder of
 * 10 instead of adding 2 to the weights it had already picked, and had no upper bound on the
 * "mid-range" check at all, so every code >= 30_000_000 - including the many real codes above
 * 60_000_000 - was validated with the wrong weight set. If it's still 10 after the fallback, the
 * check digit is defined as 0.
 */
public class EdrpouValidator implements ConstraintValidator<Edrpou, String> {

    private static final int[] WEIGHTS_1 = {1, 2, 3, 4, 5, 6, 7};
    private static final int[] WEIGHTS_1_FALLBACK = {3, 4, 5, 6, 7, 8, 9};
    private static final int[] WEIGHTS_2 = {7, 1, 2, 3, 4, 5, 6};
    private static final int[] WEIGHTS_2_FALLBACK = {9, 3, 4, 5, 6, 7, 8};
    private static final long MID_RANGE_LOWER_BOUND = 30_000_000L;
    private static final long MID_RANGE_UPPER_BOUND = 60_000_000L;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        if (!value.matches("\\d{8}")) {
            return false;
        }

        int[] digits = value.chars().map(c -> c - '0').toArray();
        long code = Long.parseLong(value);
        boolean midRange = code >= MID_RANGE_LOWER_BOUND && code <= MID_RANGE_UPPER_BOUND;
        int[] weights = midRange ? WEIGHTS_2 : WEIGHTS_1;
        int[] fallbackWeights = midRange ? WEIGHTS_2_FALLBACK : WEIGHTS_1_FALLBACK;

        int checkDigit = weightedMod11(digits, weights);
        if (checkDigit == 10) {
            checkDigit = weightedMod11(digits, fallbackWeights);
        }
        if (checkDigit == 10) {
            checkDigit = 0;
        }

        return checkDigit == digits[7];
    }

    public static boolean isValid(String value) {
        return new EdrpouValidator().isValid(value, null);
    }

    private static int weightedMod11(int[] digits, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += digits[i] * weights[i];
        }
        return sum % 11;
    }
}

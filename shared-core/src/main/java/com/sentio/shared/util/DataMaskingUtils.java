package com.sentio.shared.util;

import java.time.LocalDate;
import org.springframework.util.StringUtils;

public final class DataMaskingUtils {

    private static final char DEFAULT_MASK_CHAR = '*';

    private DataMaskingUtils() {
        throw new UnsupportedOperationException();
    }

    public static String mask(final String value, int visiblePrefix, int visibleSuffix, char maskChar) {
        if (!StringUtils.hasText(value)) {
            return value;
        }

        String trimmed = value.trim();
        int length = trimmed.length();
        int visibleTotal = visiblePrefix + visibleSuffix;

        if (length <= visibleTotal) {
            return String.valueOf(maskChar).repeat(length);
        }

        int maskCount = length - visibleTotal;

        return trimmed.substring(0, visiblePrefix)
                + String.valueOf(maskChar).repeat(maskCount)
                + trimmed.substring(length - visibleSuffix, length);
    }

    public static String mask(final String value, int visiblePrefix, int visibleSuffix) {
        return mask(value, visiblePrefix, visibleSuffix, DEFAULT_MASK_CHAR);
    }

    public static String maskRnokpp(final String rnokpp) {
        return mask(rnokpp, 3, 3); // 123****890
    }

    public static String maskPassport(final String passport) {
        return mask(passport, 2, 2); // AA****56 або 00*****67
    }

    public static String maskBirthDate(final LocalDate birthDate) {
        return (birthDate == null) ? null : "**.**." + birthDate.getYear();
    }
}

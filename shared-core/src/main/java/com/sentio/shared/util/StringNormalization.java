package com.sentio.shared.util;

import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.util.StringUtils;

public final class StringNormalization {

    private StringNormalization() {
        throw new UnsupportedOperationException();
    }

    public static String blankToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.strip();
        return StringUtils.hasText(trimmed) ? trimmed : null;
    }

    public static JsonNullable<String> blankToNull(JsonNullable<String> value) {
        if (value == null || !value.isPresent()) {
            return value;
        }

        return JsonNullable.of(blankToNull(value.get()));
    }
}

package com.sentio.shared.util;

import java.util.function.Consumer;
import org.openapitools.jackson.nullable.JsonNullable;

/**
 * MapStruct implicit-conversion helper: without this, MapStruct can't map a {@code
 * JsonNullable<T>} field on a create-request record to a plain {@code T} field on an entity - it
 * needs a single-argument method returning the target type to pick up automatically. Referenced
 * via {@code @Mapper(uses = JsonNullableSupport.class)} rather than duplicated as a local default
 * method on every mapper (was previously duplicated in {@code ClientMapper}/{@code CaseMapper}).
 *
 * <p>Only for full-object creation mapping (undefined/absent -> null, same as a real {@code
 * null}). For PATCH-style partial updates, "absent" (don't touch the field) and "present but
 * null" (clear the field) are different and must stay distinguished - see {@code
 * ClientMapper.setIfPresent}, which deliberately does not go through this method.
 */
public final class JsonNullableSupport {

    private JsonNullableSupport() {
        throw new UnsupportedOperationException();
    }

    public static <T> T unwrap(JsonNullable<T> nullable) {
        return nullable == null || !nullable.isPresent() ? null : nullable.get();
    }

    public static <T> void setIfPresent(JsonNullable<T> value, Consumer<T> setter) {
        if (value != null && value.isPresent()) {
            setter.accept(value.get());
        }
    }
}

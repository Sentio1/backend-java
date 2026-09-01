package com.sentio.shared.annotations;

import java.util.Objects;
import java.util.function.Function;

@FunctionalInterface
public interface TriFunction<V1, V2, V3, R> {

    R apply(V1 v1, V2 v2, V3 v3);

    default <V> TriFunction<V1, V2, V3, V> andThen(Function<? super R, ? extends V> after) {
        Objects.requireNonNull(after);
        return (V1 v1, V2 v2, V3 v3) -> after.apply(apply(v1, v2, v3));
    }
}

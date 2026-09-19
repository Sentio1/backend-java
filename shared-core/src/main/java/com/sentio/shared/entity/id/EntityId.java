package com.sentio.shared.entity.id;

public interface EntityId {

    long id();

    static long requirePositive(long value, String entityName) {
        if (value <= 0) {
            throw new IllegalArgumentException(entityName + " must be positive, but was: " + value);
        }
        return value;
    }
}

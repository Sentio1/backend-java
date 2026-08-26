package com.lisovskyi.core_service.client.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates a РНОКПП (Ukrainian individual tax id): exactly 10 digits, with the 10th being a
 * check digit derived from the first 9 (see {@link RnokppValidator}). Catches transposed/mistyped
 * digits that a plain {@code @Size}/{@code @Pattern} would let through - worth it here since a bad
 * tax id on a client record eventually breaks a real court filing.
 *
 * <p>{@code null}/blank pass - this only judges shape, not whether the field is required for a
 * given {@code ClientType} (that's {@code ClientCreateRequest.isValidNaming()}'s job).
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = RnokppValidator.class)
public @interface Rnokpp {
    String message() default "Invalid RNOKPP: must be 10 digits with a valid check digit";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

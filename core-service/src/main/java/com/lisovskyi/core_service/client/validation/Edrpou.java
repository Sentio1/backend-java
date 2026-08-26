package com.lisovskyi.core_service.client.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates a ЄДРПОУ (Ukrainian legal-entity registration code): exactly 8 digits, with the 8th
 * being a check digit derived from the first 7 (see {@link EdrpouValidator}).
 *
 * <p>{@code null}/blank pass - this only judges shape, not whether the field is required for a
 * given {@code ClientType} (that's {@code ClientCreateRequest.isValidNaming()}'s job).
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EdrpouValidator.class)
public @interface Edrpou {
    String message() default "Invalid EDRPOU: must be 8 digits with a valid check digit";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

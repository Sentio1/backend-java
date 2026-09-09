package com.lisovskyi.core_service.case_.validation;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = CaseNumberValidator.class)
@Target({METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE})
@Retention(RUNTIME)
public @interface CaseNumber {

    String message() default "Invalid case number format (expected EDRSR format, e.g. 761/1234/25)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

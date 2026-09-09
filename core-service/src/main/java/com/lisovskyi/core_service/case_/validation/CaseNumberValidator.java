package com.lisovskyi.core_service.case_.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

public class CaseNumberValidator implements ConstraintValidator<CaseNumber, String> {

    private static final Pattern CASE_NUMBER_PATTERN = Pattern.compile("^\\d{1,5}/\\d{1,8}/\\d{2}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (!StringUtils.hasText(value)) {
            return true;
        }
        return CASE_NUMBER_PATTERN.matcher(value.strip()).matches();
    }
}

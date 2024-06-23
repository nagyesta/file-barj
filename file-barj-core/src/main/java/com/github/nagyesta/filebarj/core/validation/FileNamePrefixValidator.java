package com.github.nagyesta.filebarj.core.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.hibernate.validator.internal.constraintvalidators.bv.NotBlankValidator;
import org.hibernate.validator.internal.constraintvalidators.bv.PatternValidator;

public class FileNamePrefixValidator implements ConstraintValidator<FileNamePrefix, String> {

    private final PatternValidator patternValidator = new PatternValidator();
    private final NotBlankValidator notBlankValidator = new NotBlankValidator();

    @Override
    public void initialize(final FileNamePrefix constraintAnnotation) {
        ConstraintValidator.super.initialize(constraintAnnotation);
        patternValidator.initialize(constraintAnnotation.pattern());
        notBlankValidator.initialize(constraintAnnotation.notBlank());
    }

    @Override
    public boolean isValid(final String value, final ConstraintValidatorContext context) {
        context.disableDefaultConstraintViolation();
        if (!notBlankValidator.isValid(value, context)) {
            context.buildConstraintViolationWithTemplate("{jakarta.validation.constraints.NotBlank.message}")
                    .addConstraintViolation();
            return false;
        }
        if (!patternValidator.isValid(value, context)) {
            context.buildConstraintViolationWithTemplate("{jakarta.validation.constraints.Pattern.message}")
                    .addConstraintViolation();
            return false;
        }
        return true;
    }
}

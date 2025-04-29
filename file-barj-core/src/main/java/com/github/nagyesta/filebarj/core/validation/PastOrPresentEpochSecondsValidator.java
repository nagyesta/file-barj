package com.github.nagyesta.filebarj.core.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.Instant;
import java.util.Optional;

public class PastOrPresentEpochSecondsValidator implements ConstraintValidator<PastOrPresentEpochSeconds, Long> {

    @Override
    public void initialize(final PastOrPresentEpochSeconds constraintAnnotation) {
        ConstraintValidator.super.initialize(constraintAnnotation);
    }

    @Override
    public boolean isValid(
            final Long value,
            final ConstraintValidatorContext context) {
        return Instant.now().getEpochSecond() >= Optional.ofNullable(value).orElse(Long.MAX_VALUE);
    }
}

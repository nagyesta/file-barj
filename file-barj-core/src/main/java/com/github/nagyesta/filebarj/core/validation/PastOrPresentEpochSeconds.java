package com.github.nagyesta.filebarj.core.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PastOrPresentEpochSecondsValidator.class)
public @interface PastOrPresentEpochSeconds {

    /**
     * @return the error message template
     */
    String message() default "{jakarta.validation.constraints.PastOrPresent.message}";

    /**
     * @return the groups the constraint belongs to
     */
    Class<?>[] groups() default {};

    /**
     * @return the payload associated to the constraint
     */
    Class<? extends Payload>[] payload() default {};

}

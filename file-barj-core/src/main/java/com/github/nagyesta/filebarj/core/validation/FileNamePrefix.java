package com.github.nagyesta.filebarj.core.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.*;

@Documented
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = FileNamePrefixValidator.class)
public @interface FileNamePrefix {


    /**
     * @return the error message template
     */
    String message() default "Invalid file name prefix.";

    /**
     * @return the groups the constraint belongs to
     */
    Class<?>[] groups() default {};

    /**
     * @return the payload associated to the constraint
     */
    Class<? extends Payload>[] payload() default {};

    Pattern pattern() default @Pattern(regexp = "^[a-zA-Z0-9_-]+$");

    NotBlank notBlank() default @NotBlank;
}

package backend.annotations.safeText;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Short, single-line plain-text input. Rejects HTML (&lt;, &gt;), control
 * characters, and profanity. Null and blank values pass — pair with
 * {@code @NotBlank} when the field is required.
 */
@Documented
@Constraint(validatedBy = SafeTextValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface SafeText {

    String message() default "Must not contain HTML, control characters, or disallowed content";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

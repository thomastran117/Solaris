package backend.annotations.safeIdentifier;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Code-like identifier: letters, digits, hyphen, underscore, period.
 * No spaces. Also rejects profanity. Use for SKUs and location codes.
 * Null and blank values pass — pair with {@code @NotBlank} when required.
 */
@Documented
@Constraint(validatedBy = SafeIdentifierValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface SafeIdentifier {

    String message() default "May only contain letters, digits, '_', '-', '.' and no disallowed content";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

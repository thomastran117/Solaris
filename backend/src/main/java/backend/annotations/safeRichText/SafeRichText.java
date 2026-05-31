package backend.annotations.safeRichText;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Multi-line text that may contain a small HTML whitelist
 * (b, i, u, p, br, ul, ol, li, strong, em). Rejects unsafe tags/attributes
 * (script, iframe, event handlers, javascript: URLs) and profanity.
 * Null and blank values pass — pair with {@code @NotBlank} when required.
 */
@Documented
@Constraint(validatedBy = SafeRichTextValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface SafeRichText {

    String message() default "Must not contain unsafe HTML or disallowed content";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

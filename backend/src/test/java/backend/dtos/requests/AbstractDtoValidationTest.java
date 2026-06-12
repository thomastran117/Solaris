package backend.dtos.requests;

import backend.services.impl.SanitizationServiceImpl;
import backend.services.intf.SanitizationService;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.junit.jupiter.api.BeforeAll;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared base for all DTO validation tests. Sets up a Hibernate Validator instance
 * that has {@link SanitizationService} injected into the custom {@code @SafeText},
 * {@code @SafeRichText}, and {@code @SafeIdentifier} validators — without requiring
 * any Spring context.
 *
 * {@link SanitizationServiceImpl} has no Spring bean dependencies; it only reads
 * {@code profanity.txt} from the classpath via {@link #afterPropertiesSet()}.
 */
abstract class AbstractDtoValidationTest {

    protected static Validator validator;

    @BeforeAll
    static void initValidator() throws Exception {
        SanitizationServiceImpl sanitizationService = new SanitizationServiceImpl();
        sanitizationService.afterPropertiesSet(); // loads profanity.txt from classpath

        HibernateValidatorConfiguration config =
                Validation.byProvider(HibernateValidator.class).configure();

        ConstraintValidatorFactory defaultFactory = config.getDefaultConstraintValidatorFactory();

        validator = config
                .constraintValidatorFactory(new SanitizationAwareFactory(sanitizationService, defaultFactory))
                .buildValidatorFactory()
                .getValidator();
    }

    // ─── Assertion helpers ────────────────────────────────────────────────────

    protected <T> Set<ConstraintViolation<T>> violations(T obj) {
        return validator.validate(obj);
    }

    /** Asserts the object passes all constraints. */
    protected <T> void assertValid(T obj) {
        Set<ConstraintViolation<T>> v = violations(obj);
        assertTrue(v.isEmpty(),
                "Expected no violations but got: " + v.stream()
                        .map(c -> c.getPropertyPath() + ": " + c.getMessage())
                        .collect(Collectors.joining(", ")));
    }

    /** Asserts that {@code field} has at least one constraint violation. */
    protected <T> void assertViolation(T obj, String field) {
        Set<ConstraintViolation<T>> v = violations(obj);
        Set<String> paths = v.stream()
                .map(c -> c.getPropertyPath().toString())
                .collect(Collectors.toSet());
        assertFalse(v.isEmpty(), "Expected violations but got none for field: " + field);
        assertTrue(paths.contains(field),
                "Expected violation on '" + field + "' but violations were on: " + paths);
    }

    /** Asserts that the object has at least one violation (without checking the field). */
    protected <T> void assertHasAnyViolation(T obj) {
        assertFalse(violations(obj).isEmpty(), "Expected at least one violation but got none");
    }

    // ─── Custom ConstraintValidatorFactory ────────────────────────────────────

    /**
     * Wraps Hibernate's default factory. After each validator instance is created,
     * any field typed {@link SanitizationService} is set via reflection so that the
     * custom Spring-managed validators ({@code @SafeText}, {@code @SafeRichText},
     * {@code @SafeIdentifier}) work without a Spring context.
     */
    static class SanitizationAwareFactory implements ConstraintValidatorFactory {

        private final ConstraintValidatorFactory delegate;
        private final SanitizationService svc;

        SanitizationAwareFactory(SanitizationService svc, ConstraintValidatorFactory delegate) {
            this.svc = svc;
            this.delegate = delegate;
        }

        @Override
        public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
            T instance = delegate.getInstance(key);
            injectSanitizationService(instance);
            return instance;
        }

        @Override
        public void releaseInstance(ConstraintValidator<?, ?> instance) {
            delegate.releaseInstance(instance);
        }

        private void injectSanitizationService(ConstraintValidator<?, ?> validator) {
            Class<?> cls = validator.getClass();
            while (cls != null && cls != Object.class) {
                for (Field f : cls.getDeclaredFields()) {
                    if (SanitizationService.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        try {
                            f.set(validator, svc);
                        } catch (IllegalAccessException ignored) {}
                    }
                }
                cls = cls.getSuperclass();
            }
        }
    }
}

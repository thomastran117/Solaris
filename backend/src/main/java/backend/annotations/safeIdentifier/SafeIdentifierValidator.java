package backend.annotations.safeIdentifier;

import backend.services.intf.SanitizationService;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;

public class SafeIdentifierValidator implements ConstraintValidator<SafeIdentifier, String> {

    @Autowired
    private SanitizationService sanitizationService;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (sanitizationService == null) return true;
        return sanitizationService.isSafeIdentifier(value);
    }
}

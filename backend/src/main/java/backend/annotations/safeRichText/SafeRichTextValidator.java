package backend.annotations.safeRichText;

import backend.services.intf.SanitizationService;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;

public class SafeRichTextValidator implements ConstraintValidator<SafeRichText, String> {

    @Autowired
    private SanitizationService sanitizationService;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (sanitizationService == null) return true;
        return sanitizationService.isSafeRichText(value);
    }
}

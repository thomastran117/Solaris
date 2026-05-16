package backend.annotations.safeText;

import backend.services.intf.SanitizationService;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class SafeTextValidator implements ConstraintValidator<SafeText, String> {

    private static final Logger log = LoggerFactory.getLogger(SafeTextValidator.class);

    @Autowired
    private SanitizationService sanitizationService;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (sanitizationService == null) {
            log.error("SanitizationService not autowired into SafeTextValidator — rejecting input as unsafe");
            return false;
        }
        return sanitizationService.isSafePlainText(value);
    }
}

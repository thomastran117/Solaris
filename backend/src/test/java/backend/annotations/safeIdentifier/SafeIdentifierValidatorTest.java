package backend.annotations.safeIdentifier;

import backend.services.intf.SanitizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SafeIdentifierValidatorTest {

    private SanitizationService sanitizationService;
    private SafeIdentifierValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        sanitizationService = mock(SanitizationService.class);
        validator = new SafeIdentifierValidator();
        injectService(validator, sanitizationService);
    }

    // ─── null service (graceful fallback) ─────────────────────────────────────

    @Test
    void isValid_nullService_returnsTrue() throws Exception {
        // SafeIdentifierValidator returns true when service is null (unlike SafeTextValidator)
        SafeIdentifierValidator v = new SafeIdentifierValidator();
        assertTrue(v.isValid("SKU-123", null));
    }

    // ─── delegation to SanitizationService ───────────────────────────────────

    @Test
    void isValid_serviceReturnsTrue_returnsTrue() {
        when(sanitizationService.isSafeIdentifier("SKU-123")).thenReturn(true);
        assertTrue(validator.isValid("SKU-123", null));
    }

    @Test
    void isValid_serviceReturnsFalse_returnsFalse() {
        when(sanitizationService.isSafeIdentifier("SKU 123")).thenReturn(false);
        assertFalse(validator.isValid("SKU 123", null));
    }

    @Test
    void isValid_nullValue_delegatesToService() {
        when(sanitizationService.isSafeIdentifier(null)).thenReturn(true);
        assertTrue(validator.isValid(null, null));
        verify(sanitizationService).isSafeIdentifier(null);
    }

    @Test
    void isValid_codeWithDots_delegatesToService() {
        when(sanitizationService.isSafeIdentifier("sku.v2")).thenReturn(true);
        assertTrue(validator.isValid("sku.v2", null));
    }

    @Test
    void isValid_htmlInput_delegatesToService() {
        when(sanitizationService.isSafeIdentifier("SKU<script>")).thenReturn(false);
        assertFalse(validator.isValid("SKU<script>", null));
    }

    // ─── helper ──────────────────────────────────────────────────────────────

    private static void injectService(Object target, SanitizationService service) throws Exception {
        Field f = target.getClass().getDeclaredField("sanitizationService");
        f.setAccessible(true);
        f.set(target, service);
    }
}

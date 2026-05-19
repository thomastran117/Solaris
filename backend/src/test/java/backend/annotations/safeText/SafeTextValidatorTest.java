package backend.annotations.safeText;

import backend.services.intf.SanitizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SafeTextValidatorTest {

    private SanitizationService sanitizationService;
    private SafeTextValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        sanitizationService = mock(SanitizationService.class);
        validator = new SafeTextValidator();
        injectService(validator, sanitizationService);
    }

    // ─── null service (misconfigured context) ─────────────────────────────────

    @Test
    void isValid_nullService_returnsFalse() throws Exception {
        SafeTextValidator v = new SafeTextValidator(); // sanitizationService left null
        assertFalse(v.isValid("anything", null));
    }

    // ─── delegation to SanitizationService ───────────────────────────────────

    @Test
    void isValid_serviceReturnsTrue_returnsTrue() {
        when(sanitizationService.isSafePlainText("hello")).thenReturn(true);
        assertTrue(validator.isValid("hello", null));
    }

    @Test
    void isValid_serviceReturnsFalse_returnsFalse() {
        when(sanitizationService.isSafePlainText("<script>bad()</script>")).thenReturn(false);
        assertFalse(validator.isValid("<script>bad()</script>", null));
    }

    @Test
    void isValid_nullValue_delegatesToService() {
        when(sanitizationService.isSafePlainText(null)).thenReturn(true);
        assertTrue(validator.isValid(null, null));
        verify(sanitizationService).isSafePlainText(null);
    }

    @Test
    void isValid_blankValue_delegatesToService() {
        when(sanitizationService.isSafePlainText("")).thenReturn(true);
        assertTrue(validator.isValid("", null));
    }

    @Test
    void isValid_htmlInput_delegatesToService() {
        when(sanitizationService.isSafePlainText("<b>bold</b>")).thenReturn(false);
        assertFalse(validator.isValid("<b>bold</b>", null));
    }

    // ─── helper ──────────────────────────────────────────────────────────────

    private static void injectService(Object target, SanitizationService service) throws Exception {
        Field f = target.getClass().getDeclaredField("sanitizationService");
        f.setAccessible(true);
        f.set(target, service);
    }
}

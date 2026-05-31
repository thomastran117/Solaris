package backend.annotations.safeRichText;

import backend.services.intf.SanitizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SafeRichTextValidatorTest {

    private SanitizationService sanitizationService;
    private SafeRichTextValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        sanitizationService = mock(SanitizationService.class);
        validator = new SafeRichTextValidator();
        injectService(validator, sanitizationService);
    }

    // ─── null service (graceful fallback) ─────────────────────────────────────

    @Test
    void isValid_nullService_returnsTrue() throws Exception {
        // SafeRichTextValidator returns true when service is null (same as SafeIdentifier)
        SafeRichTextValidator v = new SafeRichTextValidator();
        assertTrue(v.isValid("<p>some text</p>", null));
    }

    // ─── delegation to SanitizationService ───────────────────────────────────

    @Test
    void isValid_serviceReturnsTrue_returnsTrue() {
        when(sanitizationService.isSafeRichText("<p>A <b>great</b> product.</p>")).thenReturn(true);
        assertTrue(validator.isValid("<p>A <b>great</b> product.</p>", null));
    }

    @Test
    void isValid_serviceReturnsFalse_returnsFalse() {
        when(sanitizationService.isSafeRichText("<script>bad()</script>")).thenReturn(false);
        assertFalse(validator.isValid("<script>bad()</script>", null));
    }

    @Test
    void isValid_nullValue_delegatesToService() {
        when(sanitizationService.isSafeRichText(null)).thenReturn(true);
        assertTrue(validator.isValid(null, null));
        verify(sanitizationService).isSafeRichText(null);
    }

    @Test
    void isValid_blankValue_delegatesToService() {
        when(sanitizationService.isSafeRichText("")).thenReturn(true);
        assertTrue(validator.isValid("", null));
    }

    @Test
    void isValid_eventHandler_delegatesToService() {
        when(sanitizationService.isSafeRichText("<img src=x onerror=alert(1)>")).thenReturn(false);
        assertFalse(validator.isValid("<img src=x onerror=alert(1)>", null));
    }

    // ─── helper ──────────────────────────────────────────────────────────────

    private static void injectService(Object target, SanitizationService service) throws Exception {
        Field f = target.getClass().getDeclaredField("sanitizationService");
        f.setAccessible(true);
        f.set(target, service);
    }
}

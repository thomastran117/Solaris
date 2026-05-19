package backend.annotations.strongPassword;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StrongPasswordValidatorTest {

    private StrongPasswordValidator validator;

    @BeforeEach
    void setUp() {
        StrongPassword annotation = mock(StrongPassword.class);
        when(annotation.minLength()).thenReturn(8);
        validator = new StrongPasswordValidator();
        validator.initialize(annotation);
    }

    // ─── null / blank ─────────────────────────────────────────────────────────

    @Test
    void isValid_null_returnsFalse() {
        assertFalse(validator.isValid(null, null));
    }

    // ─── length ──────────────────────────────────────────────────────────────

    @Test
    void isValid_tooShort_returnsFalse() {
        assertFalse(validator.isValid("P@ss1!", null)); // 6 chars
    }

    @Test
    void isValid_exactMinLength_withAllRequirements_returnsTrue() {
        assertFalse(validator.isValid("P@ss1!!", null)); // 7 chars
        assertTrue(validator.isValid("P@ss1!!!",  null)); // 8 chars, all rules met
    }

    // ─── character class requirements ─────────────────────────────────────────

    @Test
    void isValid_missingUppercase_returnsFalse() {
        assertFalse(validator.isValid("p@ssword1!", null));
    }

    @Test
    void isValid_missingLowercase_returnsFalse() {
        assertFalse(validator.isValid("P@SSWORD1!", null));
    }

    @Test
    void isValid_missingDigit_returnsFalse() {
        assertFalse(validator.isValid("P@ssword!!", null));
    }

    @Test
    void isValid_missingSpecialChar_returnsFalse() {
        assertFalse(validator.isValid("Password1A", null));
    }

    // ─── special character set ────────────────────────────────────────────────

    @Test
    void isValid_eachAllowedSpecialChar_returnsTrue() {
        // Special chars: @ $ ! % * ? & . _ -
        for (char c : new char[]{'@', '$', '!', '%', '*', '?', '&', '.', '_', '-'}) {
            String pw = "Passw0rd" + c;
            assertTrue(validator.isValid(pw, null), "Expected valid for special char: " + c);
        }
    }

    @Test
    void isValid_disallowedSpecialChar_returnsFalse() {
        // # is not in the allowed special character set
        assertFalse(validator.isValid("Passw0rd#", null));
    }

    // ─── all rules met ────────────────────────────────────────────────────────

    @Test
    void isValid_allRequirementsMet_returnsTrue() {
        assertTrue(validator.isValid("P@ssword1!", null));
        assertTrue(validator.isValid("MySecure.Pass99", null));
        assertTrue(validator.isValid("Tr0ub4d&r", null));
    }

    // ─── custom minLength ────────────────────────────────────────────────────

    @Test
    void isValid_customMinLength_enforcedCorrectly() {
        StrongPassword annotation = mock(StrongPassword.class);
        when(annotation.minLength()).thenReturn(12);
        StrongPasswordValidator v = new StrongPasswordValidator();
        v.initialize(annotation);

        assertFalse(v.isValid("P@ssword1!", null)); // 10 chars — valid pattern but < 12
        assertTrue(v.isValid("P@ssword1!aB", null)); // 12 chars — valid
    }
}

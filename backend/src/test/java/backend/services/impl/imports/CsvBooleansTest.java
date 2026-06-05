package backend.services.impl.imports;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CsvBooleansTest {

    // ── Null / blank ──────────────────────────────────────────────────────────

    @Test
    void nullInput_returnsNull() {
        assertNull(CsvBooleans.parse(null));
    }

    @Test
    void emptyString_returnsNull() {
        assertNull(CsvBooleans.parse(""));
    }

    @Test
    void whitespaceOnly_returnsNull() {
        assertNull(CsvBooleans.parse("   "));
    }

    // ── Truthy values ─────────────────────────────────────────────────────────

    @Test
    void true_returnsTrue() {
        assertTrue(CsvBooleans.parse("true"));
    }

    @Test
    void yes_returnsTrue() {
        assertTrue(CsvBooleans.parse("yes"));
    }

    @Test
    void y_returnsTrue() {
        assertTrue(CsvBooleans.parse("y"));
    }

    @Test
    void one_returnsTrue() {
        assertTrue(CsvBooleans.parse("1"));
    }

    @Test
    void on_returnsTrue() {
        assertTrue(CsvBooleans.parse("on"));
    }

    @Test
    void trueUppercase_returnsTrue() {
        assertTrue(CsvBooleans.parse("TRUE"));
    }

    @Test
    void yesWithSpaces_returnsTrue() {
        assertTrue(CsvBooleans.parse("  YES  "));
    }

    // ── Falsy values ──────────────────────────────────────────────────────────

    @Test
    void false_returnsFalse() {
        assertFalse(CsvBooleans.parse("false"));
    }

    @Test
    void no_returnsFalse() {
        assertFalse(CsvBooleans.parse("no"));
    }

    @Test
    void n_returnsFalse() {
        assertFalse(CsvBooleans.parse("n"));
    }

    @Test
    void zero_returnsFalse() {
        assertFalse(CsvBooleans.parse("0"));
    }

    @Test
    void off_returnsFalse() {
        assertFalse(CsvBooleans.parse("off"));
    }

    @Test
    void falseUppercase_returnsFalse() {
        assertFalse(CsvBooleans.parse("FALSE"));
    }

    // ── Unrecognised value ────────────────────────────────────────────────────

    @Test
    void unrecognisedValue_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> CsvBooleans.parse("maybe"));
    }

    @Test
    void numericTwo_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> CsvBooleans.parse("2"));
    }
}

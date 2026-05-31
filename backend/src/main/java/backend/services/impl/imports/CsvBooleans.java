package backend.services.impl.imports;

import java.util.Set;

final class CsvBooleans {
    private CsvBooleans() {}

    private static final Set<String> TRUTHY = Set.of("true", "yes", "y", "1", "on");
    private static final Set<String> FALSY = Set.of("false", "no", "n", "0", "off");

    /** Returns null for blank/missing; throws IllegalArgumentException for unrecognised values. */
    static Boolean parse(String raw) {
        if (raw == null) return null;
        String v = raw.trim().toLowerCase();
        if (v.isEmpty()) return null;
        if (TRUTHY.contains(v)) return Boolean.TRUE;
        if (FALSY.contains(v)) return Boolean.FALSE;
        throw new IllegalArgumentException("Expected true/false/yes/no/1/0 but got '" + raw + "'");
    }
}

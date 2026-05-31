package backend.testutil;

import java.util.UUID;

/**
 * Deterministic UUID factory for test fixtures. Maps small int seeds to stable UUIDs
 * so existing tests that referenced numeric IDs (1L, 2L, ...) translate cleanly to
 * UUID-based assertions and Mockito stubs.
 */
public final class TestIds {

    private TestIds() {}

    /**
     * Returns a deterministic UUID for the given seed.
     * uuid(1) = 00000000-0000-0000-0000-000000000001 etc.
     */
    public static UUID uuid(int seed) {
        return new UUID(0L, seed);
    }

    public static UUID uuid(long seed) {
        return new UUID(0L, seed);
    }
}

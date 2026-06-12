package backend.utilities.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class LoggerImplTest {

    private LoggerImpl logger;

    @BeforeEach
    void setUp() {
        logger = new LoggerImpl();
    }

    // ── Single-argument methods ───────────────────────────────────────────────

    @Test
    void info_singleArg_doesNotThrow() {
        assertDoesNotThrow(() -> logger.info("hello from info"));
    }

    @Test
    void debug_singleArg_doesNotThrow() {
        assertDoesNotThrow(() -> logger.debug("hello from debug"));
    }

    @Test
    void warn_singleArg_doesNotThrow() {
        assertDoesNotThrow(() -> logger.warn("hello from warn"));
    }

    @Test
    void error_singleArg_doesNotThrow() {
        assertDoesNotThrow(() -> logger.error("hello from error"));
    }

    @Test
    void critical_singleArg_doesNotThrow() {
        assertDoesNotThrow(() -> logger.critical("critical alert"));
    }

    // ── Format methods (varargs) ──────────────────────────────────────────────

    @Test
    void info_format_doesNotThrow() {
        assertDoesNotThrow(() -> logger.info("value=%d", 42));
    }

    @Test
    void info_formatNoArgs_doesNotThrow() {
        assertDoesNotThrow(() -> logger.info("no args here"));
    }

    @Test
    void debug_format_doesNotThrow() {
        assertDoesNotThrow(() -> logger.debug("key=%s value=%s", "k", "v"));
    }

    @Test
    void warn_format_doesNotThrow() {
        assertDoesNotThrow(() -> logger.warn("threshold exceeded: %d%%", 95));
    }

    @Test
    void error_format_doesNotThrow() {
        assertDoesNotThrow(() -> logger.error("failure for id=%s", "abc-123"));
    }

    @Test
    void critical_format_doesNotThrow() {
        assertDoesNotThrow(() -> logger.critical("system failure: %s", "disk full"));
    }

    @Test
    void critical_formatNoArgs_doesNotThrow() {
        assertDoesNotThrow(() -> logger.critical("no args critical"));
    }
}

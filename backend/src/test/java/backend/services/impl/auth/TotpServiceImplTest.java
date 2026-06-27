package backend.services.impl.auth;

import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TotpServiceImplTest {

    private TotpServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TotpServiceImpl();
    }

    // ─── generateSecret ──────────────────────────────────────────────────────

    @Test
    void generateSecret_returnsNonBlankString() {
        String secret = service.generateSecret();
        assertNotNull(secret);
        assertFalse(secret.isBlank());
    }

    @Test
    void generateSecret_returnsBase32Characters() {
        String secret = service.generateSecret();
        assertTrue(secret.matches("[A-Z2-7]+"),
                "TOTP secret should be a Base32 string, got: " + secret);
    }

    // ─── getOtpAuthUri ────────────────────────────────────────────────────────

    @Test
    void getOtpAuthUri_containsSchemeAndSecret() {
        String secret = service.generateSecret();
        String uri = service.getOtpAuthUri(secret, "user@example.com");
        assertTrue(uri.startsWith("otpauth://totp/"), "URI should start with otpauth://totp/");
        assertTrue(uri.contains("secret=" + secret), "URI should contain the secret");
    }

    @Test
    void getOtpAuthUri_containsIssuerShopWave() {
        String uri = service.getOtpAuthUri(service.generateSecret(), "user@example.com");
        assertTrue(uri.contains("issuer=ShopWave"), "URI should contain issuer=ShopWave");
    }

    // ─── verifyCode ──────────────────────────────────────────────────────────

    @Test
    void verifyCode_withCurrentlyValidCode_returnsTrue() throws Exception {
        String secret = service.generateSecret();
        CodeGenerator gen = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6);
        long bucket = new SystemTimeProvider().getTime() / 30;
        String code = gen.generate(secret, bucket);

        assertTrue(service.verifyCode(secret, code));
    }

    @Test
    void verifyCode_withInvalidCode_returnsFalse() {
        String secret = service.generateSecret();
        assertFalse(service.verifyCode(secret, "000000"));
    }

    @Test
    void verifyCode_withNullSecret_returnsFalse() {
        assertFalse(service.verifyCode(null, "123456"));
    }

    @Test
    void verifyCode_withNullCode_returnsFalse() {
        assertFalse(service.verifyCode(service.generateSecret(), null));
    }

    @Test
    void verifyCode_withBlankCode_returnsFalse() {
        assertFalse(service.verifyCode(service.generateSecret(), "   "));
    }

    // ─── generateBackupCodes ─────────────────────────────────────────────────

    @Test
    void generateBackupCodes_returnsRequestedCount() {
        List<String> codes = service.generateBackupCodes(8);
        assertEquals(8, codes.size());
    }

    @Test
    void generateBackupCodes_allCodesAreUnique() {
        List<String> codes = service.generateBackupCodes(8);
        long distinct = codes.stream().distinct().count();
        assertEquals(8, distinct, "All backup codes should be unique");
    }

    @Test
    void generateBackupCodes_eachCodeHasCorrectLength() {
        List<String> codes = service.generateBackupCodes(5);
        for (String code : codes) {
            assertEquals(10, code.length(), "Each backup code should be 10 characters");
        }
    }

    @Test
    void generateBackupCodes_codesContainOnlyAllowedCharacters() {
        List<String> codes = service.generateBackupCodes(10);
        for (String code : codes) {
            assertTrue(code.matches("[A-HJ-NP-Z2-9]+"),
                    "Backup code contains unexpected character: " + code);
        }
    }

    // ─── hashBackupCode / verifyBackupCode ───────────────────────────────────

    @Test
    void hashBackupCode_producesNonBlankHash() {
        String hash = service.hashBackupCode("ABCD123456");
        assertNotNull(hash);
        assertFalse(hash.isBlank());
    }

    @Test
    void hashBackupCode_hashVerifiesAgainstOriginalPlaintext() {
        String plain = "ABCD123456";
        String hash = service.hashBackupCode(plain);
        assertTrue(service.verifyBackupCode(plain, List.of(hash)));
    }

    @Test
    void verifyBackupCode_matchFoundInList_returnsTrue() {
        String plain = "TESTCODE12";
        String hash = service.hashBackupCode(plain);
        assertTrue(service.verifyBackupCode(plain, List.of("wronghash1", hash, "wronghash2")));
    }

    @Test
    void verifyBackupCode_noMatchInList_returnsFalse() {
        String hash = service.hashBackupCode("RIGHTCODE1");
        assertFalse(service.verifyBackupCode("WRONGCODE1", List.of(hash)));
    }

    @Test
    void verifyBackupCode_nullPlainCode_returnsFalse() {
        assertFalse(service.verifyBackupCode(null, List.of("somehash")));
    }

    @Test
    void verifyBackupCode_nullHashList_returnsFalse() {
        assertFalse(service.verifyBackupCode("ABCD123456", null));
    }
}

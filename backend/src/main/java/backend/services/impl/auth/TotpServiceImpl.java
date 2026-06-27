package backend.services.impl.auth;

import backend.services.intf.auth.TotpService;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

@Service
public class TotpServiceImpl implements TotpService {

    private static final String ISSUER = "ShopWave";
    private static final String BACKUP_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int BACKUP_CODE_LENGTH = 10;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator(32);
    private final TimeProvider timeProvider = new SystemTimeProvider();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6);
    private final DefaultCodeVerifier codeVerifier;
    private final SecureRandom random = new SecureRandom();

    public TotpServiceImpl() {
        this.codeVerifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
        // Allow 1 window of drift (±30 s) to account for slight clock skew.
        this.codeVerifier.setAllowedTimePeriodDiscrepancy(1);
    }

    @Override
    public String generateSecret() {
        return secretGenerator.generate();
    }

    @Override
    public String getOtpAuthUri(String secret, String userEmail) {
        String encodedLabel = encode(ISSUER + ":" + userEmail);
        String encodedIssuer = encode(ISSUER);
        return "otpauth://totp/" + encodedLabel
                + "?secret=" + secret
                + "&issuer=" + encodedIssuer
                + "&algorithm=SHA1&digits=6&period=30";
    }

    @Override
    public boolean verifyCode(String secret, String code) {
        if (secret == null || secret.isBlank() || code == null || code.isBlank()) {
            return false;
        }
        try {
            return codeVerifier.isValidCode(secret, code);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<String> generateBackupCodes(int count) {
        List<String> codes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            codes.add(randomCode());
        }
        return codes;
    }

    @Override
    public String hashBackupCode(String plainCode) {
        return BCrypt.hashpw(plainCode, BCrypt.gensalt(10));
    }

    @Override
    public boolean verifyBackupCode(String plainCode, List<String> hashes) {
        if (plainCode == null || hashes == null) {
            return false;
        }
        return hashes.stream().anyMatch(h -> {
            try {
                return BCrypt.checkpw(plainCode, h);
            } catch (IllegalArgumentException e) {
                return false;
            }
        });
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(BACKUP_CODE_LENGTH);
        for (int i = 0; i < BACKUP_CODE_LENGTH; i++) {
            sb.append(BACKUP_CODE_CHARS.charAt(random.nextInt(BACKUP_CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private static String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8)
                    .replace("+", "%20");
        } catch (Exception e) {
            return value;
        }
    }
}

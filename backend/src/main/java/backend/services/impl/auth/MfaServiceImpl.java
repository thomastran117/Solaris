package backend.services.impl.auth;

import backend.dtos.responses.mfa.MfaCredentialResponse;
import backend.dtos.responses.mfa.TotpEnrollmentResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.MfaCredential;
import backend.models.core.User;
import backend.models.enums.MfaType;
import backend.repositories.MfaCredentialRepository;
import backend.services.intf.CacheService;
import backend.services.intf.auth.MfaOtpService;
import backend.services.intf.auth.MfaService;
import backend.services.intf.auth.TotpService;
import backend.services.intf.auth.UserService;
import backend.services.intf.notification.SmsService;
import backend.services.intf.support.EmailService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class MfaServiceImpl implements MfaService {

    static final String TOTP_PENDING_PREFIX  = "mfa:totp:pending:";
    static final long   TOTP_PENDING_TTL     = 600L; // 10 minutes
    static final int    BACKUP_CODE_COUNT     = 8;

    private final MfaCredentialRepository mfaRepo;
    private final MfaOtpService mfaOtpService;
    private final TotpService totpService;
    private final CacheService cacheService;
    private final EmailService emailService;
    private final SmsService smsService;
    private final UserService userService;

    public MfaServiceImpl(MfaCredentialRepository mfaRepo,
                          MfaOtpService mfaOtpService,
                          TotpService totpService,
                          CacheService cacheService,
                          EmailService emailService,
                          SmsService smsService,
                          UserService userService) {
        this.mfaRepo       = mfaRepo;
        this.mfaOtpService = mfaOtpService;
        this.totpService   = totpService;
        this.cacheService  = cacheService;
        this.emailService  = emailService;
        this.smsService    = smsService;
        this.userService   = userService;
    }

    // ─── Email enrollment ─────────────────────────────────────────────────────

    @Override
    public void initiateEmailEnrollment(UUID userId) {
        User user = userService.getUserByID(userId);
        String code = mfaOtpService.generateEmailOtp(userId);
        emailService.sendMfaOtpEmail(user.getEmail(), code);
    }

    @Override
    @Transactional
    public void verifyEmailEnrollment(UUID userId, String code) {
        mfaOtpService.verifyEmailOtp(userId, code);
        User user = userService.getUserByID(userId);
        MfaCredential credential = mfaRepo.findByUserIdAndType(userId, MfaType.EMAIL)
                .orElseGet(MfaCredential::new);
        credential.setUser(user);
        credential.setType(MfaType.EMAIL);
        credential.setVerified(true);
        credential.setEnabled(true);
        credential.setTarget(user.getEmail());
        credential.setSecret(null);
        credential.getBackupCodeHashes().clear();
        mfaRepo.save(credential);
    }

    // ─── SMS enrollment ───────────────────────────────────────────────────────

    @Override
    public void initiateSmsEnrollment(UUID userId, String phoneNumber) {
        String code = mfaOtpService.generateSmsOtp(userId, phoneNumber);
        smsService.sendSms(phoneNumber, "Your ShopWave verification code is: " + code
                + ". It expires in 5 minutes. Do not share this code.");
    }

    @Override
    @Transactional
    public void verifySmsEnrollment(UUID userId, String code) {
        String phoneNumber = mfaOtpService.verifySmsOtp(userId, code);
        User user = userService.getUserByID(userId);
        MfaCredential credential = mfaRepo.findByUserIdAndType(userId, MfaType.SMS)
                .orElseGet(MfaCredential::new);
        credential.setUser(user);
        credential.setType(MfaType.SMS);
        credential.setVerified(true);
        credential.setEnabled(true);
        credential.setTarget(phoneNumber);
        credential.setSecret(null);
        credential.getBackupCodeHashes().clear();
        mfaRepo.save(credential);
    }

    // ─── TOTP enrollment ──────────────────────────────────────────────────────

    @Override
    public TotpEnrollmentResponse initiateTotpEnrollment(UUID userId) {
        User user = userService.getUserByID(userId);
        String secret = totpService.generateSecret();
        cacheService.set(TOTP_PENDING_PREFIX + userId, secret, TOTP_PENDING_TTL);
        String otpAuthUri = totpService.getOtpAuthUri(secret, user.getEmail());
        return new TotpEnrollmentResponse(secret, otpAuthUri);
    }

    @Override
    @Transactional
    public List<String> verifyTotpEnrollment(UUID userId, String code) {
        // Read (do NOT consume) the pending secret: a wrong/typo'd code must not destroy it,
        // otherwise the user's authenticator app desyncs and they must re-scan a new QR.
        // The secret is consumed only after the credential is durably persisted below.
        String secret = cacheService.get(TOTP_PENDING_PREFIX + userId);
        if (secret == null) {
            throw new BadRequestException("TOTP setup has expired. Please restart enrollment.");
        }
        if (!totpService.verifyCode(secret, code)) {
            throw new BadRequestException("Invalid TOTP code.");
        }
        List<String> plainCodes  = totpService.generateBackupCodes(BACKUP_CODE_COUNT);
        List<String> hashedCodes = plainCodes.stream()
                .map(totpService::hashBackupCode)
                .toList();

        User user = userService.getUserByID(userId);
        MfaCredential credential = mfaRepo.findByUserIdAndType(userId, MfaType.TOTP)
                .orElseGet(MfaCredential::new);
        credential.setUser(user);
        credential.setType(MfaType.TOTP);
        credential.setVerified(true);
        credential.setEnabled(true);
        credential.setSecret(secret);
        credential.setTarget(null);
        credential.getBackupCodeHashes().clear();
        credential.getBackupCodeHashes().addAll(hashedCodes);
        mfaRepo.save(credential);

        // Enrollment succeeded — retire the pending secret so it can't be replayed.
        cacheService.delete(TOTP_PENDING_PREFIX + userId);

        return plainCodes;
    }

    // ─── Management ───────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<MfaCredentialResponse> listMethods(UUID userId) {
        return mfaRepo.findByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public MfaCredentialResponse setMethodEnabled(UUID userId, MfaType type, boolean enabled) {
        MfaCredential credential = mfaRepo.findByUserIdAndType(userId, type)
                .orElseThrow(() -> new ResourceNotFoundException("MFA method not enrolled."));
        credential.setEnabled(enabled);
        return toResponse(mfaRepo.save(credential));
    }

    @Override
    @Transactional
    public void removeMethod(UUID userId, MfaType type) {
        MfaCredential credential = mfaRepo.findByUserIdAndType(userId, type)
                .orElseThrow(() -> new ResourceNotFoundException("MFA method not enrolled."));
        mfaRepo.delete(credential);
    }

    // ─── Mapping ──────────────────────────────────────────────────────────────

    private MfaCredentialResponse toResponse(MfaCredential c) {
        return new MfaCredentialResponse(
                c.getId(),
                c.getType(),
                c.isVerified(),
                c.isEnabled(),
                maskTarget(c.getType(), c.getTarget()),
                c.getCreatedAt()
        );
    }

    private String maskTarget(MfaType type, String target) {
        if (target == null) return null;
        return switch (type) {
            case SMS -> {
                int len = target.length();
                yield len > 4 ? "***" + target.substring(len - 4) : "****";
            }
            case EMAIL -> {
                int at = target.indexOf('@');
                if (at <= 0) yield "***@***";
                String local  = target.substring(0, at);
                String domain = target.substring(at);
                yield (local.length() > 2 ? local.charAt(0) + "***" : "***") + domain;
            }
            case TOTP -> null;
        };
    }
}

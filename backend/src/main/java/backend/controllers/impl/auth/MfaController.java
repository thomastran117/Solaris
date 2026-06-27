package backend.controllers.impl.auth;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.mfa.InitiateSmsEnrollmentRequest;
import backend.dtos.requests.mfa.UpdateMfaMethodRequest;
import backend.dtos.requests.mfa.VerifyMfaEnrollmentRequest;
import backend.dtos.responses.mfa.MfaCredentialResponse;
import backend.dtos.responses.mfa.TotpEnrollmentResponse;
import backend.dtos.responses.mfa.TotpVerifyResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.models.enums.MfaType;
import backend.services.intf.RateLimitService;
import backend.services.intf.auth.MfaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/mfa")
@RequireAuth
public class MfaController {

    // OTP-sending endpoints are throttled per user. SMS additionally throttles per target
    // number so a single account cannot be used to bomb one victim. TOTP only mints a local
    // secret (no external send), so its budget is looser.
    private static final int  OTP_WINDOW_SECONDS  = 900;  // 15 minutes
    private static final int  EMAIL_ENROLL_LIMIT  = 5;
    private static final int  SMS_ENROLL_LIMIT    = 5;
    private static final int  SMS_PER_PHONE_LIMIT = 3;
    private static final int  SMS_PHONE_WINDOW    = 3600; // 1 hour
    private static final int  TOTP_ENROLL_LIMIT   = 10;

    // Verify endpoints are throttled per user too. This matters most for TOTP, whose pending
    // secret is intentionally preserved across wrong guesses — without a budget that endpoint
    // would be an unbounded compute sink (and a brute-force surface once reused by the challenge flow).
    private static final int  VERIFY_LIMIT        = 10;

    private final MfaService mfaService;
    private final RateLimitService rateLimitService;

    public MfaController(MfaService mfaService, RateLimitService rateLimitService) {
        this.mfaService = mfaService;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping
    public ResponseEntity<List<MfaCredentialResponse>> listMethods() {
        try {
            return ResponseEntity.ok(mfaService.listMethods(resolveUserId()));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/enroll/email")
    public ResponseEntity<Void> initiateEmailEnrollment() {
        try {
            UUID userId = resolveUserId();
            rateLimitService.enforce("mfa:enroll:email", userId.toString(), EMAIL_ENROLL_LIMIT, OTP_WINDOW_SECONDS);
            mfaService.initiateEmailEnrollment(userId);
            return ResponseEntity.ok().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/verify/email")
    public ResponseEntity<Void> verifyEmailEnrollment(@Valid @RequestBody VerifyMfaEnrollmentRequest request) {
        try {
            UUID userId = resolveUserId();
            rateLimitService.enforce("mfa:verify:email", userId.toString(), VERIFY_LIMIT, OTP_WINDOW_SECONDS);
            mfaService.verifyEmailEnrollment(userId, request.code());
            return ResponseEntity.ok().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/enroll/sms")
    public ResponseEntity<Void> initiateSmsEnrollment(@Valid @RequestBody InitiateSmsEnrollmentRequest request) {
        try {
            UUID userId = resolveUserId();
            rateLimitService.enforce("mfa:enroll:sms", userId.toString(), SMS_ENROLL_LIMIT, OTP_WINDOW_SECONDS);
            rateLimitService.enforce("mfa:enroll:sms:phone", request.phoneNumber(), SMS_PER_PHONE_LIMIT, SMS_PHONE_WINDOW);
            mfaService.initiateSmsEnrollment(userId, request.phoneNumber());
            return ResponseEntity.ok().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/verify/sms")
    public ResponseEntity<Void> verifySmsEnrollment(@Valid @RequestBody VerifyMfaEnrollmentRequest request) {
        try {
            UUID userId = resolveUserId();
            rateLimitService.enforce("mfa:verify:sms", userId.toString(), VERIFY_LIMIT, OTP_WINDOW_SECONDS);
            mfaService.verifySmsEnrollment(userId, request.code());
            return ResponseEntity.ok().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/enroll/totp")
    public ResponseEntity<TotpEnrollmentResponse> initiateTotpEnrollment() {
        try {
            UUID userId = resolveUserId();
            rateLimitService.enforce("mfa:enroll:totp", userId.toString(), TOTP_ENROLL_LIMIT, OTP_WINDOW_SECONDS);
            TotpEnrollmentResponse response = mfaService.initiateTotpEnrollment(userId);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(response);
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/verify/totp")
    public ResponseEntity<TotpVerifyResponse> verifyTotpEnrollment(@Valid @RequestBody VerifyMfaEnrollmentRequest request) {
        try {
            UUID userId = resolveUserId();
            rateLimitService.enforce("mfa:verify:totp", userId.toString(), VERIFY_LIMIT, OTP_WINDOW_SECONDS);
            List<String> backupCodes = mfaService.verifyTotpEnrollment(userId, request.code());
            // Backup codes are shown exactly once — keep them out of client/proxy caches.
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(new TotpVerifyResponse(backupCodes));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PatchMapping("/{type}")
    public ResponseEntity<MfaCredentialResponse> updateMethod(@PathVariable MfaType type,
                                                              @Valid @RequestBody UpdateMfaMethodRequest request) {
        try {
            return ResponseEntity.ok(mfaService.setMethodEnabled(resolveUserId(), type, request.enabled()));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @DeleteMapping("/{type}")
    public ResponseEntity<Void> removeMethod(@PathVariable MfaType type) {
        try {
            mfaService.removeMethod(resolveUserId(), type);
            return ResponseEntity.noContent().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    private UUID resolveUserId() {
        return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}

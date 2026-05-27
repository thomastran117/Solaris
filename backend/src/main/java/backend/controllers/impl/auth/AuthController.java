package backend.controllers.impl.auth;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import backend.configurations.environment.EnvironmentSetting;
import backend.http.ClientInfo;
import backend.http.ClientRequestContext;
import backend.services.intf.AuthAuditLogger;
import backend.services.intf.CaptchaService;
import backend.services.intf.RateLimitService;
import backend.services.intf.auth.AuthService;
import backend.services.intf.auth.DeviceService;
import backend.utilities.intf.Logger;
import backend.dtos.requests.auth.SignupRequest;
import backend.dtos.responses.auth.AuthResponse;
import backend.dtos.responses.auth.DeviceVerificationRequiredResponse;
import backend.dtos.responses.auth.TokenResponse;
import backend.dtos.responses.device.DeviceResponse;
import backend.dtos.responses.general.MessageResponse;
import backend.dtos.requests.auth.LoginRequest;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.InternalServerErrorException;
import backend.exceptions.http.ServiceUnavaliableException;
import backend.exceptions.http.UnauthorizedException;
import backend.annotations.requireAuth.RequireAuth;
import backend.security.oauth.InvalidOAuthTokenException;
import backend.security.oauth.OAuthProviderNotConfiguredException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/auth")
public class AuthController {


    private final AuthService authService;
    private final DeviceService deviceService;
    private final Logger logger;
    private final EnvironmentSetting env;
    private final RateLimitService rateLimitService;
    private final AuthAuditLogger audit;
    private final CaptchaService captchaService;

    public AuthController(AuthService authService,
                          DeviceService deviceService, Logger logger,
                          EnvironmentSetting env, RateLimitService rateLimitService,
                          AuthAuditLogger audit, CaptchaService captchaService) {
        this.authService = authService;
        this.deviceService = deviceService;
        this.logger = logger;
        this.env = env;
        this.rateLimitService = rateLimitService;
        this.audit = audit;
        this.captchaService = captchaService;
    }

    private String clientIp() {
        ClientInfo info = ClientRequestContext.get();
        String ip = info != null ? info.ip() : null;
        return ip == null || ip.isBlank() ? "unknown" : ip;
    }

    private String emailKey(String email) {
        // Lower-case to avoid trivially bypassing the per-account bucket by varying case.
        return email == null ? "" : email.trim().toLowerCase();
    }

    private ResponseCookie buildRefreshCookie(String value, long maxAgeSeconds) {
        EnvironmentSetting.Security.Cookie cfg = env.getSecurity().getCookie();
        return ResponseCookie.from("refreshToken", value == null ? "" : value)
                .httpOnly(true)
                .secure(cfg.isSecure())
                .sameSite(cfg.getSameSite())
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        EnvironmentSetting.Security.RateLimits rl = env.getSecurity().getRateLimits();
        rateLimitService.enforce("auth:login:ip", clientIp(), rl.getLoginPerIpLimit(), rl.getWindowSeconds());
        rateLimitService.enforce("auth:login:email", emailKey(request.getEmail()),
                rl.getLoginPerEmailLimit(), rl.getWindowSeconds());
        rateLimitService.enforceLoginLockout(emailKey(request.getEmail()));
        verifyCaptchaOrThrow(request.getCaptcha());
        try {
            AuthService.LoginAttemptResult attempt = authService.localAuthenicate(
                    request.getEmail(), request.getPassword());

            if (attempt.deviceVerificationRequired()) {
                rateLimitService.clearLoginFailures(emailKey(request.getEmail()));
                audit.log(AuthAuditLogger.Event.DEVICE_VERIFICATION_REQUIRED, emailKey(request.getEmail()), null);
                return ResponseEntity.ok(DeviceVerificationRequiredResponse.standard());
            }
            rateLimitService.clearLoginFailures(emailKey(request.getEmail()));
            audit.log(AuthAuditLogger.Event.LOGIN_SUCCESS, emailKey(request.getEmail()), null);
            return buildLoginResponse(attempt.loginResult(), response);
        } catch (UnauthorizedException e) {
            rateLimitService.recordLoginFailure(emailKey(request.getEmail()),
                    rl.getLockoutThreshold(), rl.getLockoutWindowSeconds(), rl.getLockoutDurationSeconds());
            audit.log(AuthAuditLogger.Event.LOGIN_FAILURE, emailKey(request.getEmail()), e.getClass().getSimpleName());
            throw e;
        } catch (AppHttpException e) {
            audit.log(AuthAuditLogger.Event.LOGIN_FAILURE, emailKey(request.getEmail()), e.getClass().getSimpleName());
            throw e;
        } catch (Exception e) {
            audit.log(AuthAuditLogger.Event.LOGIN_FAILURE, emailKey(request.getEmail()), "internal-error");
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest request) {
        EnvironmentSetting.Security.RateLimits rl = env.getSecurity().getRateLimits();
        rateLimitService.enforce("auth:signup:ip", clientIp(), rl.getSignupPerIpLimit(), rl.getWindowSeconds());
        verifyCaptchaOrThrow(request.getCaptcha());
        try {
            AuthService.SignupResult result = authService.signup(
                    request.getEmail(),
                    request.getPassword()
            );
            audit.log(AuthAuditLogger.Event.SIGNUP, emailKey(request.getEmail()), null);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new MessageResponse(result.message()));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyEmail(@RequestParam(name = "token") String token) {
        EnvironmentSetting.Security.RateLimits rl = env.getSecurity().getRateLimits();
        rateLimitService.enforce("auth:verify:ip", clientIp(), rl.getVerifyPerIpLimit(), rl.getWindowSeconds());
        try {
            authService.verifyEmail(token);
            return ResponseEntity.ok(new MessageResponse("Email verified successfully."));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/verify-device")
    public ResponseEntity<?> verifyDevice(@RequestParam(name = "token") String token,
                                          HttpServletResponse response) {
        EnvironmentSetting.Security.RateLimits rl = env.getSecurity().getRateLimits();
        rateLimitService.enforce("auth:verify-device:ip", clientIp(), rl.getVerifyDevicePerIpLimit(), rl.getWindowSeconds());
        try {
            AuthService.LoginResult result = authService.verifyDevice(token);
            return buildLoginResponse(result, response);
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @RequireAuth
    @GetMapping("/devices")
    public ResponseEntity<?> listDevices() {
        try {
            UUID userId = resolveUserId();
            List<DeviceResponse> devices = deviceService.getDevicesForUser(userId).stream()
                    .map(d -> new DeviceResponse(
                            d.getId(),
                            d.getFingerprint(),
                            d.getDeviceType().name(),
                            d.getBrowser(),
                            d.getOs(),
                            d.getLastIp(),
                            d.getCreatedAt(),
                            d.getLastSeenAt()
                    ))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(devices);
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @RequireAuth
    @DeleteMapping("/devices/{id}")
    public ResponseEntity<?> removeDevice(@PathVariable UUID id) {
        try {
            UUID userId = resolveUserId();
            deviceService.removeDevice(userId, id);
            return ResponseEntity.noContent().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refreshToken(HttpServletRequest request, HttpServletResponse response) {
        EnvironmentSetting.Security.RateLimits rl = env.getSecurity().getRateLimits();
        rateLimitService.enforce("auth:refresh:ip", clientIp(), rl.getRefreshPerIpLimit(), rl.getWindowSeconds());
        String refreshToken = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("refreshToken".equals(cookie.getName())) {
                    refreshToken = cookie.getValue();
                    break;
                }
            }
        }

        AuthService.RefreshResult result = authService.refresh(refreshToken);

        ResponseCookie cookie = buildRefreshCookie(result.refreshToken(), 7L * 24 * 60 * 60);
        response.addHeader("Set-Cookie", cookie.toString());

        audit.log(AuthAuditLogger.Event.REFRESH, null, null);
        return ResponseEntity.ok(new TokenResponse(
                result.accessToken(),
                "Bearer",
                result.expiresInSeconds(),
                result.email(),
                result.role(),
                result.tier()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("refreshToken".equals(cookie.getName())) {
                    refreshToken = cookie.getValue();
                    break;
                }
            }
        }
        if (refreshToken != null) {
            authService.revokeRefreshToken(refreshToken);
        }
        ResponseCookie clearCookie = buildRefreshCookie("", 0);
        response.addHeader("Set-Cookie", clearCookie.toString());
        audit.log(AuthAuditLogger.Event.LOGOUT, null, null);
        return ResponseEntity.ok(new MessageResponse("Logged out."));
    }

    @PostMapping("/google")
    public ResponseEntity<?> googleLogin(@RequestBody Map<String, String> body, HttpServletResponse response) {
        EnvironmentSetting.Security.RateLimits rl = env.getSecurity().getRateLimits();
        rateLimitService.enforce("auth:oauth:ip", clientIp(), rl.getOauthPerIpLimit(), rl.getWindowSeconds());
        String idTokenString = body.get("idToken");
        if (idTokenString == null) {
            throw new BadRequestException("Missing idToken");
        }

        try {
            AuthService.LoginAttemptResult attempt = authService.googleAuthenicate(idTokenString);

            if (attempt.deviceVerificationRequired()) {
                return ResponseEntity.ok(DeviceVerificationRequiredResponse.standard());
            }
            return buildLoginResponse(attempt.loginResult(), response);
        } catch (OAuthProviderNotConfiguredException e) {
            throw new ServiceUnavaliableException("Google sign-in is not available on this server");
        } catch (InvalidOAuthTokenException e) {
            throw new UnauthorizedException("Invalid or expired Google token");
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/apple")
    public ResponseEntity<?> appleLogin(@RequestBody Map<String, String> body, HttpServletResponse response) {
        EnvironmentSetting.Security.RateLimits rl = env.getSecurity().getRateLimits();
        rateLimitService.enforce("auth:oauth:ip", clientIp(), rl.getOauthPerIpLimit(), rl.getWindowSeconds());
        String idTokenString = body.get("idToken");
        if (idTokenString == null) {
            throw new BadRequestException("Missing idToken");
        }

        try {
            AuthService.LoginAttemptResult attempt = authService.appleAuthenticate(idTokenString);

            if (attempt.deviceVerificationRequired()) {
                return ResponseEntity.ok(DeviceVerificationRequiredResponse.standard());
            }
            return buildLoginResponse(attempt.loginResult(), response);
        } catch (OAuthProviderNotConfiguredException e) {
            throw new ServiceUnavaliableException("Apple sign-in is not available on this server");
        } catch (InvalidOAuthTokenException e) {
            throw new UnauthorizedException("Invalid or expired Apple token");
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/microsoft")
    public ResponseEntity<?> microsoftLogin(@RequestBody Map<String, String> body, HttpServletResponse response) {
        EnvironmentSetting.Security.RateLimits rl = env.getSecurity().getRateLimits();
        rateLimitService.enforce("auth:oauth:ip", clientIp(), rl.getOauthPerIpLimit(), rl.getWindowSeconds());
        String idTokenString = body.get("idToken");
        if (idTokenString == null) {
            throw new BadRequestException("Missing idToken");
        }

        try {
            AuthService.LoginAttemptResult attempt = authService.microsoftAuthenticate(idTokenString);

            if (attempt.deviceVerificationRequired()) {
                return ResponseEntity.ok(DeviceVerificationRequiredResponse.standard());
            }
            return buildLoginResponse(attempt.loginResult(), response);
        } catch (OAuthProviderNotConfiguredException e) {
            throw new ServiceUnavaliableException("Microsoft sign-in is not available on this server");
        } catch (InvalidOAuthTokenException e) {
            throw new UnauthorizedException("Invalid or expired Microsoft token");
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @RequireAuth
    @GetMapping("/hello")
    public String Hello() {
        return "hello authorized user!";
    }

    // ---- private helpers ----

    private void verifyCaptchaOrThrow(String token) {
        if (!captchaService.verify(token, clientIp())) {
            throw new BadRequestException("Captcha verification failed.");
        }
    }

    private ResponseEntity<?> buildLoginResponse(AuthService.LoginResult result, HttpServletResponse response) {
        ResponseCookie cookie = buildRefreshCookie(result.refreshToken(), 7L * 24 * 60 * 60);
        response.addHeader("Set-Cookie", cookie.toString());
        return ResponseEntity.ok(
                new AuthResponse(result.accessToken(), result.email(), result.usertype(), result.userId(), result.tier()));
    }

    private UUID resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (UUID) auth.getPrincipal();
    }
}

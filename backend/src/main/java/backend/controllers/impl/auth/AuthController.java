package backend.controllers.impl.auth;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import backend.configurations.environment.EnvironmentSetting;
import backend.http.ClientInfo;
import backend.http.ClientRequestContext;
import backend.services.intf.AuthAuditLogger;
import backend.services.intf.RateLimitService;
import backend.services.intf.auth.AuthService;
import backend.services.intf.auth.DeviceService;
import backend.services.intf.auth.OAuthService;
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
import backend.models.other.OAuthUser;
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

    // Conservative defaults — tune via config if needed. The IP buckets stop generic
    // floods; the email bucket stops credential-stuffing against a single account from
    // many IPs.
    private static final int LOGIN_PER_IP_LIMIT      = 10;
    private static final int LOGIN_PER_EMAIL_LIMIT   = 5;
    private static final int SIGNUP_PER_IP_LIMIT     = 5;
    private static final int OAUTH_PER_IP_LIMIT      = 10;
    private static final int VERIFY_PER_IP_LIMIT     = 10;
    private static final int REFRESH_PER_IP_LIMIT    = 30;
    private static final int RATE_WINDOW_SECONDS     = 60;

    private final AuthService authService;
    private final OAuthService oauthService;
    private final DeviceService deviceService;
    private final Logger logger;
    private final EnvironmentSetting env;
    private final RateLimitService rateLimitService;
    private final AuthAuditLogger audit;

    public AuthController(AuthService authService, OAuthService oauthService,
                          DeviceService deviceService, Logger logger,
                          EnvironmentSetting env, RateLimitService rateLimitService,
                          AuthAuditLogger audit) {
        this.authService = authService;
        this.oauthService = oauthService;
        this.deviceService = deviceService;
        this.logger = logger;
        this.env = env;
        this.rateLimitService = rateLimitService;
        this.audit = audit;
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
        rateLimitService.enforce("auth:login:ip", clientIp(), LOGIN_PER_IP_LIMIT, RATE_WINDOW_SECONDS);
        rateLimitService.enforce("auth:login:email", emailKey(request.getEmail()),
                LOGIN_PER_EMAIL_LIMIT, RATE_WINDOW_SECONDS);
        try {
            AuthService.LoginAttemptResult attempt = authService.localAuthenicate(
                    request.getEmail(), request.getPassword());

            if (attempt.deviceVerificationRequired()) {
                audit.log(AuthAuditLogger.Event.DEVICE_VERIFICATION_REQUIRED, emailKey(request.getEmail()), null);
                return ResponseEntity.ok(DeviceVerificationRequiredResponse.standard());
            }
            audit.log(AuthAuditLogger.Event.LOGIN_SUCCESS, emailKey(request.getEmail()), null);
            return buildLoginResponse(attempt.loginResult(), response);
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
        rateLimitService.enforce("auth:signup:ip", clientIp(), SIGNUP_PER_IP_LIMIT, RATE_WINDOW_SECONDS);
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
        rateLimitService.enforce("auth:verify:ip", clientIp(), VERIFY_PER_IP_LIMIT, RATE_WINDOW_SECONDS);
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
        rateLimitService.enforce("auth:verify-device:ip", clientIp(), VERIFY_PER_IP_LIMIT, RATE_WINDOW_SECONDS);
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
        rateLimitService.enforce("auth:refresh:ip", clientIp(), REFRESH_PER_IP_LIMIT, RATE_WINDOW_SECONDS);
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
                result.expiresInSeconds()
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
        rateLimitService.enforce("auth:oauth:ip", clientIp(), OAUTH_PER_IP_LIMIT, RATE_WINDOW_SECONDS);
        String idTokenString = body.get("idToken");
        if (idTokenString == null) {
            throw new BadRequestException("Missing idToken");
        }

        try {
            OAuthUser oauthUser = oauthService.verifyGoogleToken(idTokenString);

            AuthService.LoginAttemptResult attempt = authService.googleAuthenicate(oauthUser.email());

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
        rateLimitService.enforce("auth:oauth:ip", clientIp(), OAUTH_PER_IP_LIMIT, RATE_WINDOW_SECONDS);
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
        rateLimitService.enforce("auth:oauth:ip", clientIp(), OAUTH_PER_IP_LIMIT, RATE_WINDOW_SECONDS);
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

    private ResponseEntity<?> buildLoginResponse(AuthService.LoginResult result, HttpServletResponse response) {
        ResponseCookie cookie = buildRefreshCookie(result.refreshToken(), 7L * 24 * 60 * 60);
        response.addHeader("Set-Cookie", cookie.toString());
        return ResponseEntity.ok(
                new AuthResponse(result.accessToken(), result.email(), result.usertype(), result.userId()));
    }

    private UUID resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (UUID) auth.getPrincipal();
    }
}

package backend.controllers.impl;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import backend.services.intf.AuthService;
import backend.services.intf.DeviceService;
import backend.services.intf.OAuthService;
import backend.utilities.intf.Logger;
import backend.dtos.requests.auth.SignupRequest;
import backend.dtos.responses.auth.AuthResponse;
import backend.dtos.responses.device.DeviceResponse;
import backend.dtos.responses.general.MessageResponse;
import backend.dtos.requests.auth.LoginRequest;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
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

    private final AuthService authService;
    private final OAuthService oauthService;
    private final DeviceService deviceService;
    private final Logger logger;

    public AuthController(AuthService authService, OAuthService oauthService,
                          DeviceService deviceService, Logger logger) {
        this.authService = authService;
        this.oauthService = oauthService;
        this.deviceService = deviceService;
        this.logger = logger;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        try {
            AuthService.LoginAttemptResult attempt = authService.localAuthenicate(
                    request.getEmail(), request.getPassword());

            if (attempt.deviceVerificationRequired()) {
                return ResponseEntity.ok(Map.of(
                        "status", "DEVICE_VERIFICATION_REQUIRED",
                        "message", "A verification email has been sent to your inbox. Please verify this device to continue."
                ));
            }
            return buildLoginResponse(attempt.loginResult(), response);
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest request) {
        try {
            AuthService.SignupResult result = authService.signup(
                    request.getEmail(),
                    request.getPassword(),
                    request.getRole() != null ? request.getRole().toString() : "USER"
            );
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
            long userId = resolveUserId();
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
    public ResponseEntity<?> removeDevice(@PathVariable long id) {
        try {
            long userId = resolveUserId();
            deviceService.removeDevice(userId, id);
            return ResponseEntity.noContent().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("refreshToken".equals(cookie.getName())) {
                    refreshToken = cookie.getValue();
                    break;
                }
            }
        }

        try {
            AuthService.RefreshResult result = authService.refresh(refreshToken);

            ResponseCookie cookie = ResponseCookie.from("refreshToken", result.refreshToken())
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(7 * 24 * 60 * 60)
                .build();

            response.addHeader("Set-Cookie", cookie.toString());

            return ResponseEntity.ok(Map.of(
                    "accessToken", result.accessToken(),
                    "tokenType", "Bearer",
                    "expiresIn", result.expiresInSeconds()
            ));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
        }
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
        ResponseCookie clearCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader("Set-Cookie", clearCookie.toString());
        return ResponseEntity.ok(new MessageResponse("Logged out."));
    }

    @PostMapping("/google")
    public ResponseEntity<?> googleLogin(@RequestBody Map<String, String> body, HttpServletResponse response) {
        String idTokenString = body.get("idToken");
        if (idTokenString == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing idToken"));
        }

        try {
            OAuthUser oauthUser = oauthService.verifyGoogleToken(idTokenString);

            AuthService.LoginAttemptResult attempt = authService.googleAuthenicate(oauthUser.email());

            if (attempt.deviceVerificationRequired()) {
                return ResponseEntity.ok(Map.of(
                        "status", "DEVICE_VERIFICATION_REQUIRED",
                        "message", "A verification email has been sent to your inbox. Please verify this device to continue."
                ));
            }
            return buildLoginResponse(attempt.loginResult(), response);
        } catch (OAuthProviderNotConfiguredException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", "Google sign-in is not available on this server"));
        } catch (InvalidOAuthTokenException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid or expired Google token"));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/apple")
    public ResponseEntity<?> appleLogin(@RequestBody Map<String, String> body, HttpServletResponse response) {
        String idTokenString = body.get("idToken");
        if (idTokenString == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing idToken"));
        }

        try {
            AuthService.LoginAttemptResult attempt = authService.appleAuthenticate(idTokenString);

            if (attempt.deviceVerificationRequired()) {
                return ResponseEntity.ok(Map.of(
                        "status", "DEVICE_VERIFICATION_REQUIRED",
                        "message", "A verification email has been sent to your inbox. Please verify this device to continue."
                ));
            }
            return buildLoginResponse(attempt.loginResult(), response);
        } catch (OAuthProviderNotConfiguredException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", "Apple sign-in is not available on this server"));
        } catch (InvalidOAuthTokenException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid or expired Apple token"));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/microsoft")
    public ResponseEntity<?> microsoftLogin(@RequestBody Map<String, String> body, HttpServletResponse response) {
        String idTokenString = body.get("idToken");
        if (idTokenString == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing idToken"));
        }

        try {
            AuthService.LoginAttemptResult attempt = authService.microsoftAuthenticate(idTokenString);

            if (attempt.deviceVerificationRequired()) {
                return ResponseEntity.ok(Map.of(
                        "status", "DEVICE_VERIFICATION_REQUIRED",
                        "message", "A verification email has been sent to your inbox. Please verify this device to continue."
                ));
            }
            return buildLoginResponse(attempt.loginResult(), response);
        } catch (OAuthProviderNotConfiguredException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", "Microsoft sign-in is not available on this server"));
        } catch (InvalidOAuthTokenException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid or expired Microsoft token"));
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
        ResponseCookie cookie = ResponseCookie.from("refreshToken", result.refreshToken())
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(7 * 24 * 60 * 60)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
        return ResponseEntity.ok(
                new AuthResponse(result.accessToken(), result.email(), result.usertype(), result.userId()));
    }

    private long resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return ((Number) auth.getPrincipal()).longValue();
    }
}

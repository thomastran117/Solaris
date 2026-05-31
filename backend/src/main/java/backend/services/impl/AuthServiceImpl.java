package backend.services.impl;

import backend.http.ClientInfo;
import backend.http.ClientRequestContext;
import backend.models.core.User;
import backend.models.other.OAuthUser;
import backend.services.intf.AuthService;
import backend.services.intf.DeviceService;
import backend.services.intf.EmailVerificationService;
import backend.services.intf.OAuthService;
import backend.services.intf.TokenService;
import backend.services.intf.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final TokenService tokenService;
    private final OAuthService oauthService;
    private final EmailVerificationService emailVerificationService;
    private final DeviceService deviceService;

    public AuthServiceImpl(UserService userService,
                           OAuthService oauthService,
                           TokenService tokenService,
                           EmailVerificationService emailVerificationService,
                           DeviceService deviceService) {
        this.userService = userService;
        this.tokenService = tokenService;
        this.oauthService = oauthService;
        this.emailVerificationService = emailVerificationService;
        this.deviceService = deviceService;
    }

    @Override
    public LoginAttemptResult localAuthenicate(String email, String password) {
        User user = userService.login(email, password);
        return handleDeviceCheck(user);
    }

    @Override
    public RefreshResult refresh(String refreshToken) {
        if (refreshToken == null || !tokenService.validateRefreshToken(refreshToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }
        TokenService.RefreshTokenPayload payload = tokenService.getRefreshTokenPayload(refreshToken);
        if (payload == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }

        User user = userService.getUserByID(payload.userId());

        tokenService.revokeRefreshToken(refreshToken);
        String newRefreshToken = tokenService.generateRefreshToken(user.getId().intValue(), user.getRole().toString(), user.getEmail());
        String newAccessToken = tokenService.generateAccessToken(user.getId().intValue(), user.getRole().toString(), user.getEmail());
        long expiresIn = tokenService.getAccessTokenExpiresInSeconds();

        return new RefreshResult(newAccessToken, newRefreshToken, expiresIn);
    }

    @Override
    public LoginAttemptResult googleAuthenicate(String token) {
        OAuthUser oauthUser = oauthService.verifyGoogleToken(token);
        User user = userService.loginOrSignupGoogle(oauthUser.email());
        return handleDeviceCheck(user);
    }

    @Override
    public LoginAttemptResult microsoftAuthenticate(String token) {
        OAuthUser oauthUser = oauthService.verifyMicrosoftToken(token);
        User user = userService.loginOrSignupMicrosoft(oauthUser.email());
        return handleDeviceCheck(user);
    }

    @Override
    public LoginAttemptResult appleAuthenticate(String token) {
        OAuthUser oauthUser = oauthService.verifyAppleToken(token);
        User user = userService.loginOrSignupApple(oauthUser.email());
        return handleDeviceCheck(user);
    }

    @Override
    public SignupResult signup(String email, String password, String usertype) {
        User user = userService.signup(email, password, usertype);
        emailVerificationService.initiateVerification(user.getId(), user.getEmail());
        return new SignupResult(user.getEmail(),
                "Account created. Please check your email to verify your account.");
    }

    @Override
    public void verifyEmail(String token) {
        long userId = emailVerificationService.consumeVerificationToken(token);
        userService.activateUser(userId);
    }

    @Override
    public LoginResult verifyDevice(String token) {
        DeviceService.DeviceVerificationPayload payload = deviceService.consumeDeviceVerificationToken(token);
        ClientInfo reconstructed = new ClientInfo(
                payload.ip(),
                payload.deviceType(),
                payload.browser(),
                payload.os(),
                payload.userAgent()
        );
        deviceService.recordDeviceSeen(payload.userId(), reconstructed);
        User user = userService.getUserByID(payload.userId());
        return buildLoginResult(user);
    }

    @Override
    public void revokeRefreshToken(String refreshToken) {
        tokenService.revokeRefreshToken(refreshToken);
    }

    @Override
    public void revokeAllRefreshTokensForUser(int userId) {
        tokenService.revokeAllRefreshTokensForUser(userId);
    }

    // ---- private helpers ----

    private LoginAttemptResult handleDeviceCheck(User user) {
        ClientInfo clientInfo = ClientRequestContext.get();
        String fingerprint = deviceService.computeFingerprint(clientInfo.userAgent());
        if (deviceService.isKnownDevice(user.getId(), fingerprint)) {
            deviceService.recordDeviceSeen(user.getId(), clientInfo);
            return LoginAttemptResult.success(buildLoginResult(user));
        } else {
            deviceService.initiateDeviceVerification(user.getId(), user.getEmail(), clientInfo);
            return LoginAttemptResult.pendingVerification();
        }
    }

    private LoginResult buildLoginResult(User user) {
        Map<String, Object> tokens = tokenService.generateTokenPair(
                user.getId().intValue(), user.getRole().toString(), user.getEmail());
        return new LoginResult(
                (String) tokens.get("accessToken"),
                (String) tokens.get("refreshToken"),
                user.getEmail(),
                user.getRole().toString(),
                user.getId()
        );
    }
}

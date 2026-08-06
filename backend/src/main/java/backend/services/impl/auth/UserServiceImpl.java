package backend.services.impl.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.exceptions.http.UnauthorizedException;
import backend.models.core.User;
import backend.models.enums.UserRole;
import backend.models.enums.UserStatus;
import backend.models.other.OAuthUser;
import backend.repositories.UserRepository;
import backend.services.intf.AuthAuditLogger;
import backend.services.intf.auth.TokenService;
import backend.services.intf.auth.UserService;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthAuditLogger auditLogger;
    private final TokenService tokenService;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
                           AuthAuditLogger auditLogger, TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogger = auditLogger;
        this.tokenService = tokenService;
    }

    // A bcrypt hash of "dummy" — used as the comparison target when the email is not found
    // so that the response time is indistinguishable from a wrong-password attempt, closing
    // the timing side-channel that reveals whether an address is registered.
    private static final String DUMMY_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoOhJ1XIZZ3w1J3M1P1jOdFkjT3NQ5NeFe";

    /**
     * Normalises an address before it is stored or looked up.
     *
     * <p>Applied at the service boundary rather than in the controller so that every entry
     * point — form login, signup, and the OAuth providers, which supply the address
     * themselves — agrees on the stored form. Under MySQL's case-insensitive collation this
     * was harmless; PostgreSQL compares text exactly, so without it an address registered as
     * {@code User@example.com} could never be matched by a login for {@code user@example.com},
     * and the unique constraint on {@code users.email} would permit both rows to coexist.
     */
    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public User login(String email, String password) {
        Optional<User> user = userRepository.findByEmail(normalizeEmail(email));

        // Always run bcrypt regardless of whether the user exists — this equalises response
        // time and prevents enumeration via timing or distinct error messages.
        String storedHash = user.map(User::getPassword).orElse(DUMMY_HASH);
        boolean passwordMatches = passwordEncoder.matches(password, storedHash);

        if (user.isEmpty() || !passwordMatches) {
            throw new UnauthorizedException("Invalid email or password");
        }

        validateAccountAccessible(user.get());

        return user.get();
    }

    @Override
    public User signup(String email, String password) {
        String normalized = normalizeEmail(email);
        if (userRepository.findByEmail(normalized).isPresent()) {
            throw new ConflictException("An account with these credentials already exists");
        }

        User user = new User();
        user.setEmail(normalized);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.PENDING_VERIFICATION);

        return userRepository.save(user);
    }

    @Override
    public User setRole(UUID userId, UserRole role) {
        if (role == null) {
            throw new ForbiddenException("Role is required");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        user.setRole(role);
        return userRepository.save(user);
    }

    @Override
    public void activateUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }

    @Override
    public User getUserByID(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    @Override
    public User getAccessibleUserByID(UUID id) {
        User user = getUserByID(id);
        validateAccountAccessible(user);
        return user;
    }

    @Override
    public UUID getID(String email) {
        return userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email))
                .getId();
    }

    @Override
    public boolean changePassword(UUID id, String currentPassword, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new ForbiddenException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        tokenService.revokeAllRefreshTokensForUser(id);
        return true;
    }

    @Override
    public boolean delete(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        userRepository.delete(user);
        return true;
    }

    @Override
    @Transactional
    public User loginOrSignupGoogle(OAuthUser oauthUser) {
        // 1. Sub-first: stable identity lookup — immune to email reassignment.
        Optional<User> bySub = userRepository.findByGoogleId(oauthUser.sub());
        if (bySub.isPresent()) {
            validateAccountAccessible(bySub.get());
            return bySub.get();
        }

        // 2. Email fallback: existing local account linking for first-time Google login.
        Optional<User> byEmail = userRepository.findByEmail(normalizeEmail(oauthUser.email()));
        if (byEmail.isPresent()) {
            User user = byEmail.get();
            validateAccountAccessible(user);
            if (user.getGoogleId() != null) {
                // Email matched an account already linked to a different Google sub — reject.
                throw new UnauthorizedException("This email is linked to a different Google account");
            }
            user.setGoogleId(oauthUser.sub());
            userRepository.save(user);
            auditLogger.log(AuthAuditLogger.Event.OAUTH_ACCOUNT_LINKED,
                    user.getId().toString(), "provider=google");
            return user;
        }

        // 3. New user.
        User user = new User();
        user.setEmail(normalizeEmail(oauthUser.email()));
        user.setGoogleId(oauthUser.sub());
        user.setPassword(null);
        user.setRole(UserRole.USER);
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public User loginOrSignupMicrosoft(OAuthUser oauthUser) {
        Optional<User> bySub = userRepository.findByMicrosoftId(oauthUser.sub());
        if (bySub.isPresent()) {
            validateAccountAccessible(bySub.get());
            return bySub.get();
        }

        Optional<User> byEmail = userRepository.findByEmail(normalizeEmail(oauthUser.email()));
        if (byEmail.isPresent()) {
            User user = byEmail.get();
            validateAccountAccessible(user);
            if (user.getMicrosoftId() != null) {
                throw new UnauthorizedException("This email is linked to a different Microsoft account");
            }
            user.setMicrosoftId(oauthUser.sub());
            userRepository.save(user);
            auditLogger.log(AuthAuditLogger.Event.OAUTH_ACCOUNT_LINKED,
                    user.getId().toString(), "provider=microsoft");
            return user;
        }

        User user = new User();
        user.setEmail(normalizeEmail(oauthUser.email()));
        user.setMicrosoftId(oauthUser.sub());
        user.setPassword(null);
        user.setRole(UserRole.USER);
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public User loginOrSignupApple(OAuthUser oauthUser) {
        Optional<User> bySub = userRepository.findByAppleId(oauthUser.sub());
        if (bySub.isPresent()) {
            validateAccountAccessible(bySub.get());
            return bySub.get();
        }

        Optional<User> byEmail = userRepository.findByEmail(normalizeEmail(oauthUser.email()));
        if (byEmail.isPresent()) {
            User user = byEmail.get();
            validateAccountAccessible(user);
            if (user.getAppleId() != null) {
                throw new UnauthorizedException("This email is linked to a different Apple account");
            }
            user.setAppleId(oauthUser.sub());
            userRepository.save(user);
            auditLogger.log(AuthAuditLogger.Event.OAUTH_ACCOUNT_LINKED,
                    user.getId().toString(), "provider=apple");
            return user;
        }

        User user = new User();
        user.setEmail(normalizeEmail(oauthUser.email()));
        user.setAppleId(oauthUser.sub());
        user.setPassword(null);
        user.setRole(UserRole.USER);
        return userRepository.save(user);
    }

    private void validateAccountAccessible(User user) {
        UserStatus status = user.getStatus();
        if (status != UserStatus.ACTIVE && status != UserStatus.INACTIVE) {
            throw new ForbiddenException("Account is " + status.name().toLowerCase().replace('_', ' '));
        }
    }
}

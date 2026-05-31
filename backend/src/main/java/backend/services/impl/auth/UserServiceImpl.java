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

    @Override
    public User login(String email, String password) {
        Optional<User> user = userRepository.findByEmail(email);

        if (user.isEmpty()) {
            throw new UnauthorizedException("User doesn't exist");
        }

        if (!passwordEncoder.matches(password, user.get().getPassword())) {
            throw new UnauthorizedException("Invalid username or password");
        }

        validateAccountAccessible(user.get());

        return user.get();
    }

    @Override
    public User signup(String email, String password) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new ConflictException("An account with these credentials already exists");
        }

        User user = new User();
        user.setEmail(email);
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
        return userRepository.findByEmail(email)
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
        Optional<User> byEmail = userRepository.findByEmail(oauthUser.email());
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
        user.setEmail(oauthUser.email());
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

        Optional<User> byEmail = userRepository.findByEmail(oauthUser.email());
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
        user.setEmail(oauthUser.email());
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

        Optional<User> byEmail = userRepository.findByEmail(oauthUser.email());
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
        user.setEmail(oauthUser.email());
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

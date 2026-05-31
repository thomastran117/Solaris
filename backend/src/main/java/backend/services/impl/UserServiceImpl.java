package backend.services.impl;

import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.exceptions.http.UnauthorizedException;
import backend.models.core.User;
import backend.models.enums.UserRole;
import backend.models.enums.UserStatus;
import backend.repositories.UserRepository;
import backend.services.intf.UserService;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder  passwordEncoder;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder  passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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
    public User signup(String email, String password, String usertype) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new ConflictException("User already exists with email: " + email);
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.PENDING_VERIFICATION);

        return userRepository.save(user);
    }

    @Override
    public void activateUser(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }

    @Override
    public User getUserByID(long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    @Override
    public long getID(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email))
                .getId();
    }

    @Override
    public boolean changePassword(long id, String password) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        user.setPassword(passwordEncoder.encode(password));
        userRepository.save(user);
        return true;
    }

    @Override
    public boolean delete(long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        userRepository.delete(user);
        return true;
    }

    @Override
    public User loginOrSignupGoogle(String email) {
        Optional<User> existing = userRepository.findByEmail(email);

        if (existing.isPresent()) {
            validateAccountAccessible(existing.get());
            return existing.get();
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(null);
        user.setRole(UserRole.USER);
        return userRepository.save(user);
    }

    @Override
    public User loginOrSignupMicrosoft(String email) {
        Optional<User> existing = userRepository.findByEmail(email);

        if (existing.isPresent()) {
            validateAccountAccessible(existing.get());
            return existing.get();
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(null);
        user.setRole(UserRole.USER);
        return userRepository.save(user);
    }

    @Override
    public User loginOrSignupApple(String email) {
        Optional<User> existing = userRepository.findByEmail(email);

        if (existing.isPresent()) {
            validateAccountAccessible(existing.get());
            return existing.get();
        }

        User user = new User();
        user.setEmail(email);
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
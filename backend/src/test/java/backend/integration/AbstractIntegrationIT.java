package backend.integration;

import backend.http.DeviceType;
import backend.models.core.User;
import backend.models.core.UserDevice;
import backend.models.enums.UserRole;
import backend.models.enums.UserStatus;
import backend.models.other.OAuthUser;
import backend.repositories.UserDeviceRepository;
import backend.repositories.UserRepository;
import backend.services.intf.CacheService;
import backend.services.intf.auth.DeviceService;
import backend.services.intf.auth.OAuthService;
import backend.services.intf.auth.TokenService;
import backend.repositories.search.BundleSearchRepository;
import backend.repositories.search.ProductSearchRepository;
import backend.repositories.search.ReviewSearchRepository;
import backend.services.intf.support.EmailService;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Base class for all auth integration tests.
 *
 * Infrastructure provided:
 *  - TestContainers MySQL 8.0 (schema created by Hibernate create-drop)
 *  - TestContainers Redis for refresh tokens, rate limiting, and verification tokens
 *  - @MockBean for JavaMailSender, OAuthService, ElasticsearchClient, S3Client
 *  - @AfterEach cleanup of users and user_devices tables
 *  - Helper methods for creating users, generating access tokens, and building
 *    device fingerprints that match what the filter chain produces.
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@AutoConfigureMockMvc
public abstract class AbstractIntegrationIT {

    // Shared static Redis container — started once and reused across all test classes.
    // Database uses H2 in-memory (MySQL-compat mode), configured in the test properties file.
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        REDIS.start();
        registry.add("app.redis.host", REDIS::getHost);
        registry.add("app.redis.port", () -> REDIS.getMappedPort(6379));

        // Nil UUID for the risk VIP-segment sentinel — production uses -1 which is not a valid UUID
        registry.add("app.risk.vip-segment-id", () -> "00000000-0000-0000-0000-000000000000");
    }

    // ── Injected infrastructure ───────────────────────────────────────────────

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected UserRepository userRepository;
    @Autowired protected UserDeviceRepository userDeviceRepository;
    @Autowired protected JdbcTemplate jdbcTemplate;
    @Autowired protected TokenService tokenService;
    @Autowired protected DeviceService deviceService;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected CacheService cacheService;
    @Autowired protected StringRedisTemplate stringRedisTemplate;

    // ── Mocked external dependencies ──────────────────────────────────────────

    @MockitoBean protected JavaMailSender mailSender;
    @MockitoBean protected EmailService emailService;
    @MockitoBean protected OAuthService oauthService;
    @MockitoBean protected ElasticsearchClient esClient;
    @MockitoBean protected ElasticsearchOperations elasticsearchOperations;
    @MockitoBean protected BundleSearchRepository bundleSearchRepository;
    @MockitoBean protected ProductSearchRepository productSearchRepository;
    @MockitoBean protected ReviewSearchRepository reviewSearchRepository;
    @MockitoBean protected S3Client s3Client;

    // ── Known test User-Agent (produces a stable fingerprint across all tests) ─

    protected static final String TEST_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @BeforeEach
    void setUpBase() {
        // Flush all Redis state: rate-limit counters, refresh tokens, verification tokens.
        // Use execute(RedisCallback) so the connection is properly returned to the pool.
        stringRedisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Void>) conn -> {
            conn.serverCommands().flushAll();
            return null;
        });

        // JavaMailSender needs a stub for createMimeMessage() so email-sending code
        // does not NPE when the service tries to construct a MimeMessage.
        MimeMessage mime = org.mockito.Mockito.mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mime);
    }

    @AfterEach
    void cleanDatabase() {
        // Use try-catch so a missing table (e.g. user_segments, which may fail DDL in H2
        // due to reserved-word or FK issues) never blocks the users DELETE.
        try { jdbcTemplate.execute("DELETE FROM user_devices"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM user_segments"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM users"); } catch (Exception ignored) {}
    }

    // ── User factory helpers ──────────────────────────────────────────────────

    /**
     * Persists an ACTIVE user with a BCrypt-encoded password.
     */
    protected User createActiveUser(String email, String rawPassword) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    /**
     * Persists a PENDING_VERIFICATION user (can log in after email verification).
     */
    protected User createPendingUser(String email, String rawPassword) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.PENDING_VERIFICATION);
        return userRepository.save(user);
    }

    /**
     * Persists a SUSPENDED user.
     */
    protected User createSuspendedUser(String email, String rawPassword) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.SUSPENDED);
        return userRepository.save(user);
    }

    /**
     * Persists a UserDevice for the given user whose fingerprint matches
     * {@code TEST_USER_AGENT}. After this call, a login request sent with that
     * User-Agent header will be treated as a "known device" by the auth flow.
     */
    protected UserDevice registerKnownDevice(User user) {
        String fingerprint = deviceService.computeFingerprint(TEST_USER_AGENT);
        UserDevice device = new UserDevice();
        device.setUser(user);
        device.setFingerprint(fingerprint);
        device.setDeviceType(DeviceType.DESKTOP);
        device.setBrowser("Chrome 120.0.0.0");
        device.setOs("Windows");
        device.setUserAgent(TEST_USER_AGENT);
        device.setLastIp("127.0.0.1");
        device.setLastSeenAt(Instant.now());
        return userDeviceRepository.save(device);
    }

    // ── Token helpers ─────────────────────────────────────────────────────────

    /**
     * Generates a real, signed access token for the given user using the test JWT secret.
     */
    protected String accessTokenFor(User user) {
        return tokenService.generateAccessToken(
                user.getId(), user.getRole().name(), user.getEmail());
    }

    /**
     * Produces a "Bearer {token}" string ready for the Authorization header.
     */
    protected String bearer(String token) {
        return "Bearer " + token;
    }

    // ── OAuth mock helper ─────────────────────────────────────────────────────

    /**
     * Configures the mocked OAuthService to return a canned OAuthUser for any
     * token passed to verifyGoogleToken / verifyMicrosoftToken / verifyAppleToken.
     */
    protected void mockOAuthUser(String email, String provider) {
        OAuthUser oauthUser = new OAuthUser("sub-" + email, email, "Test User", provider);
        when(oauthService.verifyGoogleToken(any())).thenReturn(oauthUser);
        when(oauthService.verifyMicrosoftToken(any())).thenReturn(oauthUser);
        when(oauthService.verifyAppleToken(any())).thenReturn(oauthUser);
    }

    // ── Request body builders ─────────────────────────────────────────────────

    protected String loginBody(String email, String password) throws Exception {
        java.util.Map<String, String> body = new java.util.LinkedHashMap<>();
        body.put("email", email);
        body.put("password", password);
        body.put("captcha", "test-captcha");
        return objectMapper.writeValueAsString(body);
    }

    protected String signupBody(String email, String password) throws Exception {
        java.util.Map<String, String> body = new java.util.LinkedHashMap<>();
        body.put("email", email);
        body.put("password", password);
        body.put("captcha", "test-captcha");
        return objectMapper.writeValueAsString(body);
    }
}

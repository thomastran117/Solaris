package backend.integration.orders;

import backend.models.core.Order;
import backend.models.core.User;
import backend.models.enums.OrderStatus;
import backend.models.enums.UserRole;
import backend.models.enums.UserStatus;
import backend.repositories.OrderRepository;
import backend.repositories.UserRepository;
import backend.repositories.search.BundleSearchRepository;
import backend.repositories.search.ProductSearchRepository;
import backend.repositories.search.ReviewSearchRepository;
import backend.services.intf.CacheService;
import backend.services.intf.auth.OAuthService;
import backend.services.intf.auth.TokenService;
import backend.services.intf.support.EmailService;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.GenericContainer;
import software.amazon.awssdk.services.s3.S3Client;

import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
class DeliveryWebSocketIT {

    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        REDIS.start();
        registry.add("app.redis.host", REDIS::getHost);
        registry.add("app.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("app.risk.vip-segment-id", () -> "00000000-0000-0000-0000-000000000000");
        // Use a separate H2 database so create-drop on this RANDOM_PORT context
        // does not drop the shared shopwave_it schema used by all MockMvc IT tests.
        registry.add("app.database.url",
                () -> "jdbc:h2:mem:shopwave_ws;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE");
    }

    @LocalServerPort int port;

    @Autowired UserRepository userRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired TokenService tokenService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired CacheService cacheService;
    @Autowired StringRedisTemplate stringRedisTemplate;

    @MockitoBean JavaMailSender mailSender;
    @MockitoBean EmailService emailService;
    @MockitoBean OAuthService oauthService;
    @MockitoBean ElasticsearchClient esClient;
    @MockitoBean ElasticsearchOperations elasticsearchOperations;
    @MockitoBean BundleSearchRepository bundleSearchRepository;
    @MockitoBean ProductSearchRepository productSearchRepository;
    @MockitoBean ReviewSearchRepository reviewSearchRepository;
    @MockitoBean S3Client s3Client;

    @BeforeEach
    void setUp() {
        stringRedisTemplate.execute((RedisCallback<Void>) conn -> { conn.serverCommands().flushAll(); return null; });
        MimeMessage mime = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mime);
    }

    @AfterEach
    void cleanUp() {
        try { jdbcTemplate.execute("DELETE FROM order_status_history"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM order_items"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM orders"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM user_devices"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM user_segments"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM users"); } catch (Exception ignored) {}
    }

    private User createDriver() {
        User user = new User();
        user.setEmail("driver-" + UUID.randomUUID() + "@example.com");
        user.setPassword(passwordEncoder.encode("Password1!"));
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    private Order createOrder(User driver) {
        Order order = new Order();
        order.setUser(driver);
        order.setTotalAmount(BigDecimal.TEN);
        order.setStatus(OrderStatus.SHIPPED);
        order.setAssignedDriverId(driver.getId());
        return orderRepository.save(order);
    }

    private WebSocketStompClient buildStompClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new StringMessageConverter());
        return client;
    }

    private String wsUrl() {
        return "ws://localhost:" + port + "/ws/delivery";
    }

    @Test
    void driverCanConnectWithValidJwt() throws Exception {
        User driver = createDriver();
        String token = tokenService.generateAccessToken(
                driver.getId(), driver.getRole().name(), driver.getEmail());

        WebSocketStompClient stompClient = buildStompClient();
        AtomicBoolean connected = new AtomicBoolean(false);

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);

        stompClient.connectAsync(wsUrl(), new WebSocketHttpHeaders(), connectHeaders,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession session, StompHeaders headers) {
                        connected.set(true);
                        session.disconnect();
                    }
                });

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilTrue(connected);
        stompClient.stop();
    }

    @Test
    void sendLocationUpdate_storesInRedis() throws Exception {
        User driver = createDriver();
        Order order = createOrder(driver);
        String token = tokenService.generateAccessToken(
                driver.getId(), driver.getRole().name(), driver.getEmail());

        WebSocketStompClient stompClient = buildStompClient();
        AtomicBoolean sent = new AtomicBoolean(false);

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);

        stompClient.connectAsync(wsUrl(), new WebSocketHttpHeaders(), connectHeaders,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession session, StompHeaders headers) {
                        String body = String.format(
                                "{\"lat\":37.77,\"lng\":-122.42,\"timestamp\":\"%s\"}",
                                Instant.now());
                        session.send("/app/delivery/" + order.getId() + "/location", body);
                        sent.set(true);
                    }
                });

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilTrue(sent);
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            String cached = cacheService.get("delivery:location:" + order.getId());
            assertNotNull(cached, "Driver location should be cached in Redis after location update");
        });

        stompClient.stop();
    }
}

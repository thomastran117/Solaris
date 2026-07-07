package backend.integration.fullinfra;

import backend.models.core.User;
import backend.models.enums.UserRole;
import backend.models.enums.UserStatus;
import backend.repositories.UserRepository;
import backend.services.intf.CaptchaService;
import backend.services.intf.auth.OAuthService;
import backend.services.intf.auth.TokenService;
import backend.services.intf.support.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Base class for integration tests that need <b>live</b> Elasticsearch and Kafka in
 * addition to Redis — i.e. the search/indexing and event-flow tests.
 *
 * <p>Unlike {@link backend.integration.AbstractIntegrationIT} (which mocks the ES client +
 * search repositories and points Kafka at a dead broker), this base starts real
 * Testcontainers for Elasticsearch and Kafka and lets the production beans, indexing
 * workers, and {@code @KafkaListener} consumers run for real. It is deliberately used by
 * only a handful of classes so the fast H2+Redis suite is unaffected.
 *
 * <p>Infrastructure:
 * <ul>
 *   <li>Database: H2 in-memory (MySQL-compat), inherited from the {@code integration-test} profile.</li>
 *   <li>Redis / Elasticsearch / Kafka: real, via shared static Testcontainers.</li>
 *   <li>Only genuinely-external deps (mail, OAuth, captcha, S3) remain mocked.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles({"integration-test", "fullinfra"})
@AutoConfigureMockMvc
public abstract class AbstractSearchKafkaIT {

    // Shared static containers — started once and reused across all subclasses in the JVM.
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @SuppressWarnings("resource")
    static final ElasticsearchContainer ELASTICSEARCH =
            new ElasticsearchContainer(DockerImageName.parse(
                    "docker.elastic.co/elasticsearch/elasticsearch:8.15.3"))
                    .withEnv("xpack.security.enabled", "false")
                    .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");

    @SuppressWarnings("resource")
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

    /** Base URL of the live Elasticsearch container — captured for index refresh helpers. */
    protected static String esBaseUrl;

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        REDIS.start();
        ELASTICSEARCH.start();
        KAFKA.start();

        esBaseUrl = "http://" + ELASTICSEARCH.getHost() + ":" + ELASTICSEARCH.getMappedPort(9200);

        // Pre-create the search indices WITH the custom analyzers before the Spring context
        // starts. The Spring Data ES repository beans auto-create their index from the entity
        // mappings at bean-init, but those mappings reference custom analyzers (product_search,
        // autocomplete_*) that must live in the index *settings* first — which the repos don't
        // supply. Creating the indices up-front means the repos see them present and skip.
        createSearchIndices(esBaseUrl);

        registry.add("app.redis.host", REDIS::getHost);
        registry.add("app.redis.port", () -> REDIS.getMappedPort(6379));

        registry.add("app.elasticsearch.host", ELASTICSEARCH::getHost);
        registry.add("app.elasticsearch.port", () -> ELASTICSEARCH.getMappedPort(9200));
        registry.add("app.elasticsearch.scheme", () -> "http");

        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);

        // Nil UUID for the risk VIP-segment sentinel (RiskProperties binds it as UUID).
        registry.add("app.risk.vip-segment-id", () -> "00000000-0000-0000-0000-000000000000");
    }

    /** Index settings defining the custom analyzers referenced by the document mappings. */
    private static final String INDEX_SETTINGS = """
            {"settings":{"analysis":{
              "filter":{
                "synonym_filter":{"type":"synonym_graph","synonyms":["laptop, notebook, computer"]},
                "autocomplete_filter":{"type":"edge_ngram","min_gram":1,"max_gram":20}
              },
              "analyzer":{
                "product_search":{"type":"custom","tokenizer":"standard","filter":["lowercase","synonym_filter"]},
                "autocomplete_index":{"type":"custom","tokenizer":"standard","filter":["lowercase","autocomplete_filter"]},
                "autocomplete_search":{"type":"custom","tokenizer":"standard","filter":["lowercase"]}
              }
            }}}""";

    private static void createSearchIndices(String esBaseUrl) {
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        for (String index : new String[]{"products", "bundles", "reports", "reviews"}) {
            try {
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(esBaseUrl + "/" + index))
                        .header("Content-Type", "application/json")
                        .PUT(java.net.http.HttpRequest.BodyPublishers.ofString(INDEX_SETTINGS))
                        .build();
                java.net.http.HttpResponse<String> resp =
                        client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                // 200 = created; 400 with resource_already_exists_exception is fine on reuse.
                if (resp.statusCode() != 200
                        && !resp.body().contains("resource_already_exists_exception")) {
                    throw new IllegalStateException(
                            "Failed to pre-create ES index '" + index + "': " + resp.statusCode() + " " + resp.body());
                }
            } catch (Exception e) {
                throw new IllegalStateException("Could not pre-create ES index '" + index + "'", e);
            }
        }
    }

    // ── Injected infrastructure ───────────────────────────────────────────────

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected UserRepository userRepository;
    @Autowired protected JdbcTemplate jdbcTemplate;
    @Autowired protected TokenService tokenService;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected StringRedisTemplate stringRedisTemplate;

    // ── Mocked external dependencies (ES + Kafka are REAL, so NOT mocked here) ──

    @MockitoBean protected JavaMailSender mailSender;
    @MockitoBean protected EmailService emailService;
    @MockitoBean protected OAuthService oauthService;
    @MockitoBean protected CaptchaService captchaService;
    @MockitoBean protected S3Client s3Client;

    protected static final String TEST_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @BeforeEach
    void setUpBase() {
        stringRedisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Void>) conn -> {
            conn.serverCommands().flushAll();
            return null;
        });

        MimeMessage mime = org.mockito.Mockito.mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mime);
        when(captchaService.verify(any())).thenReturn(true);
        when(captchaService.verify(any(), any())).thenReturn(true);
    }

    @AfterEach
    void cleanUsers() {
        try { jdbcTemplate.execute("DELETE FROM user_devices"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM user_segments"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM users"); } catch (Exception ignored) {}
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    protected User createActiveUser(String email, String rawPassword) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    protected String accessTokenFor(User user) {
        return tokenService.generateAccessToken(user.getId(), user.getRole().name(), user.getEmail());
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    /**
     * Forces a refresh on all live search indices so freshly-indexed documents become visible to
     * <em>query</em> operations immediately. A GET-by-id is real-time in Elasticsearch, but search
     * queries only see documents after the (default ~1s) refresh — so tests that assert on a search
     * endpoint should await the document via {@code findById} and then call this before querying,
     * to avoid a near-real-time race (and, where results are cached, poisoning the cache with an
     * empty page).
     */
    protected void refreshSearchIndices() {
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(esBaseUrl + "/_refresh"))
                    .POST(java.net.http.HttpRequest.BodyPublishers.noBody())
                    .build();
            client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to refresh Elasticsearch indices", e);
        }
    }
}

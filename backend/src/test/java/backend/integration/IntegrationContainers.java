package backend.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Process-wide singleton backends shared by every integration test.
 *
 * <p>Surefire runs integration tests with {@code -DreuseForks=false}, i.e. one JVM per test
 * class, executed sequentially. Holding the containers in static state keeps a single
 * PostgreSQL and a single Redis alive for the whole shard instead of starting one pair per
 * test class.
 *
 * <p>Two provisioning modes:
 * <ul>
 *   <li><b>CI</b> — when {@code SHOPWAVE_TEST_DB_URL} is set, the database comes from the
 *       workflow's {@code services:} block and no container is started at all.</li>
 *   <li><b>Local</b> — otherwise a {@link PostgreSQLContainer} is started with
 *       {@code withReuse(true)}, so successive forks (and successive {@code mvnw} runs)
 *       attach to the container that is already up.</li>
 * </ul>
 *
 * <p><b>Reuse caveat:</b> a reused container outlives the build. After editing a Flyway
 * migration, remove it ({@code docker rm -f}) or the next run fails on a checksum mismatch.
 *
 * <p>The database connection is registered onto {@code app.database.*} rather than
 * {@code spring.datasource.*} because {@code AppDatabase} builds the Hikari pool by hand
 * from {@code EnvironmentSetting}; Spring Boot's {@code @ServiceConnection} would be ignored.
 */
// Containers deliberately outlive the JVM's use of them; Ryuk (or reuse) handles teardown.
@SuppressWarnings("resource")
public final class IntegrationContainers {

    private static final String ENV_URL = "SHOPWAVE_TEST_DB_URL";
    private static final String ENV_USERNAME = "SHOPWAVE_TEST_DB_USERNAME";
    private static final String ENV_PASSWORD = "SHOPWAVE_TEST_DB_PASSWORD";

    /** Null when the database is supplied externally (CI service container). */
    private static final PostgreSQLContainer<?> POSTGRES;

    private static final GenericContainer<?> REDIS;

    static {
        if (System.getenv(ENV_URL) == null) {
            POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("shopwave_it")
                    .withUsername("shopwave")
                    .withPassword("shopwave")
                    .withReuse(true)
                    // Durability is worthless for a disposable test database, and turning it
                    // off removes most of the per-test commit cost.
                    .withCommand("postgres",
                            "-c", "fsync=off",
                            "-c", "synchronous_commit=off",
                            "-c", "full_page_writes=off",
                            "-c", "max_connections=300");
            POSTGRES.start();
        } else {
            POSTGRES = null;
        }

        REDIS = new GenericContainer<>("redis:7-alpine")
                .withExposedPorts(6379)
                .withReuse(true);
        REDIS.start();
    }

    private IntegrationContainers() {
    }

    /** Points {@code app.database.*} at the shared PostgreSQL instance. */
    public static void registerDatabase(DynamicPropertyRegistry registry) {
        if (POSTGRES != null) {
            registry.add("app.database.url", POSTGRES::getJdbcUrl);
            registry.add("app.database.username", POSTGRES::getUsername);
            registry.add("app.database.password", POSTGRES::getPassword);
        } else {
            registry.add("app.database.url", () -> System.getenv(ENV_URL));
            registry.add("app.database.username", () -> System.getenv(ENV_USERNAME));
            registry.add("app.database.password", () -> System.getenv(ENV_PASSWORD));
        }
    }

    /** Points {@code app.redis.*} at the shared Redis instance. */
    public static void registerRedis(DynamicPropertyRegistry registry) {
        registry.add("app.redis.host", REDIS::getHost);
        registry.add("app.redis.port", () -> REDIS.getMappedPort(6379));
    }
}

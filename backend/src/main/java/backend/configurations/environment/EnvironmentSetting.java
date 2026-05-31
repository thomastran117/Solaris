package backend.configurations.environment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@ConfigurationProperties(prefix = "app")
public class EnvironmentSetting {

    private final Cors cors = new Cors();
    private final Security security = new Security();
    private final Redis redis = new Redis();
    private final Database database = new Database();
    private final Cache cache = new Cache();
    private final Recaptcha recaptcha = new Recaptcha();
    private final OAuth oauth = new OAuth();
    private final Proxy proxy = new Proxy();
    private final S3 s3 = new S3();
    private final Stripe stripe = new Stripe();
    private final Email email = new Email();

    public Cors getCors() {
        return cors;
    }

    public OAuth getOauth() {
        return oauth;
    }

    public Recaptcha getRecaptcha() {
        return recaptcha;
    }

    public Security getSecurity() {
        return security;
    }

    public Redis getRedis() {
        return redis;
    }

    public Database getDatabase() {
        return database;
    }

    public Cache getCache() {
        return cache;
    }

    public Proxy getProxy() {
        return proxy;
    }

    public S3 getS3() {
        return s3;
    }

    public Stripe getStripe() {
        return stripe;
    }

    public Email getEmail() {
        return email;
    }

    public static class Cors {
        private String allowedOrigins = "";

        public List<String> getAllowedOrigins() {
            if (allowedOrigins == null || allowedOrigins.isBlank()) {
                return List.of("*");
            }
            return Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }

        public void setAllowedOrigins(String allowedOrigins) {
            this.allowedOrigins = allowedOrigins != null ? allowedOrigins : "";
        }
    }

    public static class Security {
        private static final String DEFAULT_MICROSOFT_JWKS_URI = "https://login.microsoftonline.com/common/discovery/v2.0/keys";
        private static final String DEFAULT_APPLE_JWKS_URI = "https://appleid.apple.com/auth/keys";

        private final Jwt jwt = new Jwt();
        private final Jwks jwks = new Jwks();

        private String googleClientId = "";
        private String microsoftClientId = "";
        private String microsoftJwksUri = DEFAULT_MICROSOFT_JWKS_URI;
        /** Microsoft issuer authority host (e.g. https://login.microsoftonline.com/ or sovereign cloud URL). */
        private String microsoftAuthorityHost = "https://login.microsoftonline.com/";
        /** Comma-separated well-known tenant segments (e.g. common,organizations,consumers). */
        private String microsoftWellKnownTenants = "common,organizations,consumers";
        private String appleClientId = "";
        private String appleJwksUri = DEFAULT_APPLE_JWKS_URI;
        /** Max OAuth token length (bytes) to avoid oversized token attacks. Typical ID tokens < 4KB; default 16384. */
        private int oauthMaxTokenLength = 16_384;
        private String recaptchaSecretKey = "";
        /** BCrypt cost factor. Default 10 (secure for prod). Use 4–6 for local dev to reduce login latency. */
        private int bcryptStrength = 10;

        private final OAuthGoogle oauthGoogle = new OAuthGoogle();

        public Jwks getJwks() {
            return jwks;
        }

        public OAuthGoogle getOauthGoogle() {
            return oauthGoogle;
        }

        public Jwt getJwt() {
            return jwt;
        }

        public String getGoogleClientId() {
            return googleClientId != null ? googleClientId : "";
        }

        public void setGoogleClientId(String googleClientId) {
            this.googleClientId = googleClientId != null ? googleClientId : "";
        }

        public String getMicrosoftClientId() {
            return microsoftClientId != null ? microsoftClientId : "";
        }

        public void setMicrosoftClientId(String microsoftClientId) {
            this.microsoftClientId = microsoftClientId != null ? microsoftClientId : "";
        }

        public String getMicrosoftJwksUri() {
            return microsoftJwksUri != null ? microsoftJwksUri : DEFAULT_MICROSOFT_JWKS_URI;
        }

        public void setMicrosoftJwksUri(String microsoftJwksUri) {
            this.microsoftJwksUri = microsoftJwksUri != null ? microsoftJwksUri : DEFAULT_MICROSOFT_JWKS_URI;
        }

        public String getMicrosoftAuthorityHost() {
            return microsoftAuthorityHost != null ? microsoftAuthorityHost : "https://login.microsoftonline.com/";
        }

        public void setMicrosoftAuthorityHost(String microsoftAuthorityHost) {
            this.microsoftAuthorityHost = microsoftAuthorityHost != null ? microsoftAuthorityHost : "https://login.microsoftonline.com/";
        }

        public String getMicrosoftWellKnownTenants() {
            return microsoftWellKnownTenants != null ? microsoftWellKnownTenants : "common,organizations,consumers";
        }

        public void setMicrosoftWellKnownTenants(String microsoftWellKnownTenants) {
            this.microsoftWellKnownTenants = microsoftWellKnownTenants != null ? microsoftWellKnownTenants : "common,organizations,consumers";
        }

        public String getAppleClientId() {
            return appleClientId != null ? appleClientId : "";
        }

        public void setAppleClientId(String appleClientId) {
            this.appleClientId = appleClientId != null ? appleClientId : "";
        }

        public String getAppleJwksUri() {
            return appleJwksUri != null ? appleJwksUri : DEFAULT_APPLE_JWKS_URI;
        }

        public void setAppleJwksUri(String appleJwksUri) {
            this.appleJwksUri = appleJwksUri != null ? appleJwksUri : DEFAULT_APPLE_JWKS_URI;
        }

        public int getOauthMaxTokenLength() {
            return oauthMaxTokenLength > 0 ? oauthMaxTokenLength : 16_384;
        }

        public void setOauthMaxTokenLength(int oauthMaxTokenLength) {
            this.oauthMaxTokenLength = Math.max(256, Math.min(64 * 1024, oauthMaxTokenLength));
        }

        public String getRecaptchaSecretKey() {
            return recaptchaSecretKey != null ? recaptchaSecretKey : "";
        }

        public void setRecaptchaSecretKey(String recaptchaSecretKey) {
            this.recaptchaSecretKey = recaptchaSecretKey != null ? recaptchaSecretKey : "";
        }

        public int getBcryptStrength() {
            return bcryptStrength;
        }

        public void setBcryptStrength(int bcryptStrength) {
            this.bcryptStrength = Math.max(4, Math.min(31, bcryptStrength));
        }

        /**
         * JWKS endpoint settings (timeouts, connection) for OAuth JWT verification.
         * Used when fetching Microsoft JWKS to avoid blocking threads and to fail fast.
         */
        public static class Jwks {
            private int connectTimeoutMs = 5_000;
            private int readTimeoutMs = 10_000;
            /** When true, startup validates JWKS reachability. Default false so startup is not dependent on external services. */
            private boolean validateAtStartup = false;
            /** When true and validateAtStartup is true, startup fails if JWKS unreachable. When false, log warning and continue (safe for non-prod). Default false. */
            private boolean failStartupOnUnreachable = false;

            public boolean isValidateAtStartup() {
                return validateAtStartup;
            }

            public void setValidateAtStartup(boolean validateAtStartup) {
                this.validateAtStartup = validateAtStartup;
            }

            public boolean isFailStartupOnUnreachable() {
                return failStartupOnUnreachable;
            }

            public void setFailStartupOnUnreachable(boolean failStartupOnUnreachable) {
                this.failStartupOnUnreachable = failStartupOnUnreachable;
            }

            public int getConnectTimeoutMs() {
                return connectTimeoutMs;
            }

            public void setConnectTimeoutMs(int connectTimeoutMs) {
                this.connectTimeoutMs = Math.max(1_000, Math.min(30_000, connectTimeoutMs));
            }

            public int getReadTimeoutMs() {
                return readTimeoutMs;
            }

            public void setReadTimeoutMs(int readTimeoutMs) {
                this.readTimeoutMs = Math.max(1_000, Math.min(60_000, readTimeoutMs));
            }
        }

        /** Timeouts for Google OAuth token verification (cert fetch), similar to JWKS for Microsoft. */
        public static class OAuthGoogle {
            private int connectTimeoutMs = 5_000;
            private int readTimeoutMs = 10_000;

            public int getConnectTimeoutMs() {
                return connectTimeoutMs;
            }

            public void setConnectTimeoutMs(int connectTimeoutMs) {
                this.connectTimeoutMs = Math.max(1_000, Math.min(30_000, connectTimeoutMs));
            }

            public int getReadTimeoutMs() {
                return readTimeoutMs;
            }

            public void setReadTimeoutMs(int readTimeoutMs) {
                this.readTimeoutMs = Math.max(1_000, Math.min(60_000, readTimeoutMs));
            }
        }

        public static class Jwt {
            private String secret = "change-me-in-env";
            private long accessTokenTtlSeconds = 900;
            private long refreshTokenTtlSeconds = 604800;
            private String issuer = "shopwave-api";

            public String getSecret() {
                return secret != null ? secret : "";
            }

            public void setSecret(String secret) {
                this.secret = secret != null ? secret : "";
            }

            public long getAccessTokenTtlSeconds() {
                return accessTokenTtlSeconds;
            }

            public void setAccessTokenTtlSeconds(long accessTokenTtlSeconds) {
                this.accessTokenTtlSeconds = accessTokenTtlSeconds;
            }

            public long getRefreshTokenTtlSeconds() {
                return refreshTokenTtlSeconds;
            }

            public void setRefreshTokenTtlSeconds(long refreshTokenTtlSeconds) {
                this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
            }

            public String getIssuer() {
                return issuer != null ? issuer : "";
            }

            public void setIssuer(String issuer) {
                this.issuer = issuer != null ? issuer : "";
            }
        }
    }

    /**
     * reCAPTCHA verification: timeouts and retry/backoff for siteverify API calls.
     * Fail closed: on 5xx or timeout after retries, verification returns false.
     */
    public static class Recaptcha {
        private int connectTimeoutMs = 3_000;
        private int readTimeoutMs = 5_000;
        private final Retry retry = new Retry();

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = Math.max(1_000, Math.min(30_000, connectTimeoutMs));
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = Math.max(1_000, Math.min(60_000, readTimeoutMs));
        }

        public Retry getRetry() {
            return retry;
        }

        public static class Retry {
            private int maxAttempts = 4;
            private long initialIntervalMs = 200;
            private double multiplier = 2.0;
            private long maxIntervalMs = 10_000;

            public int getMaxAttempts() {
                return maxAttempts;
            }

            public void setMaxAttempts(int maxAttempts) {
                this.maxAttempts = Math.max(1, Math.min(10, maxAttempts));
            }

            public long getInitialIntervalMs() {
                return initialIntervalMs;
            }

            public void setInitialIntervalMs(long initialIntervalMs) {
                this.initialIntervalMs = Math.max(50, Math.min(60_000, initialIntervalMs));
            }

            public double getMultiplier() {
                return multiplier;
            }

            public void setMultiplier(double multiplier) {
                this.multiplier = Math.max(1.0, Math.min(5.0, multiplier));
            }

            public long getMaxIntervalMs() {
                return maxIntervalMs;
            }

            public void setMaxIntervalMs(long maxIntervalMs) {
                this.maxIntervalMs = Math.max(1_000, Math.min(300_000, maxIntervalMs));
            }
        }
    }

    /**
     * OAuth token verification: retry with exponential backoff and circuit breaker
     * for calls to Google/Microsoft (e.g. JWKS fetch). Configurable via app.oauth.*.
     */
    public static class OAuth {
        private final Retry retry = new Retry();
        private final CircuitBreaker circuitBreaker = new CircuitBreaker();
        /** When true (default), OAuthConfigValidator fails startup on missing/invalid config. Set false (e.g. local) to log and continue. */
        private boolean validateConfigAtStartup = true;

        public boolean isValidateConfigAtStartup() {
            return validateConfigAtStartup;
        }

        public void setValidateConfigAtStartup(boolean validateConfigAtStartup) {
            this.validateConfigAtStartup = validateConfigAtStartup;
        }

        public Retry getRetry() {
            return retry;
        }

        public CircuitBreaker getCircuitBreaker() {
            return circuitBreaker;
        }

        public static class Retry {
            private int maxAttempts = 4;
            private long initialIntervalMs = 200;
            private double multiplier = 2.0;
            private long maxIntervalMs = 10_000;

            public int getMaxAttempts() {
                return maxAttempts;
            }

            public void setMaxAttempts(int maxAttempts) {
                this.maxAttempts = Math.max(1, Math.min(10, maxAttempts));
            }

            public long getInitialIntervalMs() {
                return initialIntervalMs;
            }

            public void setInitialIntervalMs(long initialIntervalMs) {
                this.initialIntervalMs = Math.max(50, Math.min(60_000, initialIntervalMs));
            }

            public double getMultiplier() {
                return multiplier;
            }

            public void setMultiplier(double multiplier) {
                this.multiplier = Math.max(1.0, Math.min(5.0, multiplier));
            }

            public long getMaxIntervalMs() {
                return maxIntervalMs;
            }

            public void setMaxIntervalMs(long maxIntervalMs) {
                this.maxIntervalMs = Math.max(1_000, Math.min(300_000, maxIntervalMs));
            }
        }

        public static class CircuitBreaker {
            private float failureRateThreshold = 50f;
            private int waitDurationInOpenStateSeconds = 60;
            private int slidingWindowSize = 10;
            private int minimumNumberOfCalls = 5;

            public float getFailureRateThreshold() {
                return failureRateThreshold;
            }

            public void setFailureRateThreshold(float failureRateThreshold) {
                this.failureRateThreshold = Math.max(1f, Math.min(100f, failureRateThreshold));
            }

            public int getWaitDurationInOpenStateSeconds() {
                return waitDurationInOpenStateSeconds;
            }

            public void setWaitDurationInOpenStateSeconds(int waitDurationInOpenStateSeconds) {
                this.waitDurationInOpenStateSeconds = Math.max(1, Math.min(3600, waitDurationInOpenStateSeconds));
            }

            public int getSlidingWindowSize() {
                return slidingWindowSize;
            }

            public void setSlidingWindowSize(int slidingWindowSize) {
                this.slidingWindowSize = Math.max(2, Math.min(1000, slidingWindowSize));
            }

            public int getMinimumNumberOfCalls() {
                return minimumNumberOfCalls;
            }

            public void setMinimumNumberOfCalls(int minimumNumberOfCalls) {
                this.minimumNumberOfCalls = Math.max(1, Math.min(100, minimumNumberOfCalls));
            }
        }
    }

    public static class Cache {
        private String namespace = "app";

        public String getNamespace() {
            return namespace != null ? namespace : "app";
        }

        public void setNamespace(String namespace) {
            this.namespace = (namespace != null && !namespace.isBlank()) ? namespace : "app";
        }
    }

    public static class Redis {
        private String host = "localhost";
        private int port = 6379;
        private String password = "";
        private int database = 0;
        private int timeout = 2000;

        public String getHost() {
            return host != null ? host : "localhost";
        }

        public void setHost(String host) {
            this.host = host != null ? host : "localhost";
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getPassword() {
            return password != null ? password : "";
        }

        public void setPassword(String password) {
            this.password = password != null ? password : "";
        }

        public int getDatabase() {
            return database;
        }

        public void setDatabase(int database) {
            this.database = database;
        }

        public int getTimeout() {
            return timeout;
        }

        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }
    }

    public static class Database {
        private String url = "jdbc:mysql://localhost:3306/shopland";
        private String username = "root";
        private String password = "password123";
        private String driverClassName = "com.mysql.cj.jdbc.Driver";
        private int maximumPoolSize = 10;
        private int minimumIdle = 2;
        private long connectionTimeout = 30_000;
        private long idleTimeout = 600_000;

        public String getUrl() {
            return url != null ? url : "";
        }

        public void setUrl(String url) {
            this.url = url != null ? url : "";
        }

        public String getUsername() {
            return username != null ? username : "";
        }

        public void setUsername(String username) {
            this.username = username != null ? username : "";
        }

        public String getPassword() {
            return password != null ? password : "";
        }

        public void setPassword(String password) {
            this.password = password != null ? password : "";
        }

        public String getDriverClassName() {
            return driverClassName != null ? driverClassName : "com.mysql.cj.jdbc.Driver";
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName != null ? driverClassName : "com.mysql.cj.jdbc.Driver";
        }

        public int getMaximumPoolSize() {
            return maximumPoolSize;
        }

        public void setMaximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
        }

        public int getMinimumIdle() {
            return minimumIdle;
        }

        public void setMinimumIdle(int minimumIdle) {
            this.minimumIdle = minimumIdle;
        }

        public long getConnectionTimeout() {
            return connectionTimeout;
        }

        public void setConnectionTimeout(long connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }

        public long getIdleTimeout() {
            return idleTimeout;
        }

        public void setIdleTimeout(long idleTimeout) {
            this.idleTimeout = idleTimeout;
        }
    }

    public static class Stripe {
        private String secretKey = "";
        private String publishableKey = "";
        private String webhookSecret = "";
        private final Retry retry = new Retry();

        public String getSecretKey() {
            return secretKey != null ? secretKey : "";
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey != null ? secretKey : "";
        }

        public String getPublishableKey() {
            return publishableKey != null ? publishableKey : "";
        }

        public void setPublishableKey(String publishableKey) {
            this.publishableKey = publishableKey != null ? publishableKey : "";
        }

        public String getWebhookSecret() {
            return webhookSecret != null ? webhookSecret : "";
        }

        public void setWebhookSecret(String webhookSecret) {
            this.webhookSecret = webhookSecret != null ? webhookSecret : "";
        }

        public Retry getRetry() {
            return retry;
        }

        public static class Retry {
            private int maxAttempts = 3;
            private long initialIntervalMs = 500;
            private double multiplier = 2.0;
            private long maxIntervalMs = 8000;

            public int getMaxAttempts() {
                return maxAttempts;
            }

            public void setMaxAttempts(int maxAttempts) {
                this.maxAttempts = Math.max(1, Math.min(10, maxAttempts));
            }

            public long getInitialIntervalMs() {
                return initialIntervalMs;
            }

            public void setInitialIntervalMs(long initialIntervalMs) {
                this.initialIntervalMs = Math.max(100, Math.min(60_000, initialIntervalMs));
            }

            public double getMultiplier() {
                return multiplier;
            }

            public void setMultiplier(double multiplier) {
                this.multiplier = Math.max(1.0, Math.min(5.0, multiplier));
            }

            public long getMaxIntervalMs() {
                return maxIntervalMs;
            }

            public void setMaxIntervalMs(long maxIntervalMs) {
                this.maxIntervalMs = Math.max(1_000, Math.min(300_000, maxIntervalMs));
            }
        }
    }

    public static class S3 {
        private String region = "us-east-1";
        private String bucket = "";
        private String accessKey = "";
        private String secretKey = "";
        /** Optional CDN / public base URL (e.g. https://cdn.example.com). Falls back to the standard S3 URL when blank. */
        private String publicUrlBase = "";
        /** Pre-signed PUT URL expiry in seconds. Default 300 (5 min). */
        private int presignExpirySeconds = 300;

        public String getRegion() {
            return region != null ? region : "us-east-1";
        }

        public void setRegion(String region) {
            this.region = region != null ? region : "us-east-1";
        }

        public String getBucket() {
            return bucket != null ? bucket : "";
        }

        public void setBucket(String bucket) {
            this.bucket = bucket != null ? bucket : "";
        }

        public String getAccessKey() {
            return accessKey != null ? accessKey : "";
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey != null ? accessKey : "";
        }

        public String getSecretKey() {
            return secretKey != null ? secretKey : "";
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey != null ? secretKey : "";
        }

        public String getPublicUrlBase() {
            return publicUrlBase != null ? publicUrlBase : "";
        }

        public void setPublicUrlBase(String publicUrlBase) {
            this.publicUrlBase = publicUrlBase != null ? publicUrlBase : "";
        }

        public int getPresignExpirySeconds() {
            return presignExpirySeconds > 0 ? presignExpirySeconds : 300;
        }

        public void setPresignExpirySeconds(int presignExpirySeconds) {
            this.presignExpirySeconds = Math.max(30, Math.min(3600, presignExpirySeconds));
        }
    }

    /**
     * Trusted reverse-proxy configuration. Forwarded IP headers (X-Forwarded-For, etc.)
     * are only honoured when the direct remote address matches a trusted proxy.
     * Entries may be individual IPs or CIDR ranges (e.g. "10.0.0.0/8").
     * When the list is empty, forwarded headers are ignored and remoteAddr is always used.
     */
    public static class Proxy {
        private String trustedProxies = "";

        public List<String> getTrustedProxies() {
            if (trustedProxies == null || trustedProxies.isBlank()) {
                return List.of();
            }
            return Arrays.stream(trustedProxies.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }

        public void setTrustedProxies(String trustedProxies) {
            this.trustedProxies = trustedProxies != null ? trustedProxies : "";
        }
    }

    public static class Email {
        private String from = "noreply@shopwave.com";
        private String verificationBaseUrl = "http://localhost:3000";
        private long verificationTokenTtlSeconds = 86_400;
        private final Executor executor = new Executor();
        private final Retry retry = new Retry();

        public String getFrom() {
            return from != null ? from : "noreply@shopwave.com";
        }

        public void setFrom(String from) {
            this.from = from != null ? from : "noreply@shopwave.com";
        }

        public String getVerificationBaseUrl() {
            return verificationBaseUrl != null ? verificationBaseUrl : "http://localhost:3000";
        }

        public void setVerificationBaseUrl(String verificationBaseUrl) {
            this.verificationBaseUrl = verificationBaseUrl != null ? verificationBaseUrl : "http://localhost:3000";
        }

        public long getVerificationTokenTtlSeconds() {
            return verificationTokenTtlSeconds > 0 ? verificationTokenTtlSeconds : 86_400;
        }

        public void setVerificationTokenTtlSeconds(long verificationTokenTtlSeconds) {
            this.verificationTokenTtlSeconds = Math.max(300, Math.min(604_800, verificationTokenTtlSeconds));
        }

        private long deviceVerificationTokenTtlSeconds = 600;

        public long getDeviceVerificationTokenTtlSeconds() {
            return deviceVerificationTokenTtlSeconds > 0 ? deviceVerificationTokenTtlSeconds : 600;
        }

        public void setDeviceVerificationTokenTtlSeconds(long deviceVerificationTokenTtlSeconds) {
            this.deviceVerificationTokenTtlSeconds = Math.max(60, Math.min(3_600, deviceVerificationTokenTtlSeconds));
        }

        public Executor getExecutor() {
            return executor;
        }

        public Retry getRetry() {
            return retry;
        }

        public static class Executor {
            private int corePoolSize = 2;
            private int maxPoolSize = 4;
            private int queueCapacity = 100;

            public int getCorePoolSize() {
                return corePoolSize;
            }

            public void setCorePoolSize(int corePoolSize) {
                this.corePoolSize = Math.max(1, Math.min(20, corePoolSize));
            }

            public int getMaxPoolSize() {
                return maxPoolSize;
            }

            public void setMaxPoolSize(int maxPoolSize) {
                this.maxPoolSize = Math.max(1, Math.min(50, maxPoolSize));
            }

            public int getQueueCapacity() {
                return queueCapacity;
            }

            public void setQueueCapacity(int queueCapacity) {
                this.queueCapacity = Math.max(1, Math.min(10_000, queueCapacity));
            }
        }

        public static class Retry {
            private int maxAttempts = 3;
            private long initialIntervalMs = 1_000;
            private double multiplier = 2.0;
            private long maxIntervalMs = 10_000;

            public int getMaxAttempts() {
                return maxAttempts;
            }

            public void setMaxAttempts(int maxAttempts) {
                this.maxAttempts = Math.max(1, Math.min(10, maxAttempts));
            }

            public long getInitialIntervalMs() {
                return initialIntervalMs;
            }

            public void setInitialIntervalMs(long initialIntervalMs) {
                this.initialIntervalMs = Math.max(100, Math.min(60_000, initialIntervalMs));
            }

            public double getMultiplier() {
                return multiplier;
            }

            public void setMultiplier(double multiplier) {
                this.multiplier = Math.max(1.0, Math.min(5.0, multiplier));
            }

            public long getMaxIntervalMs() {
                return maxIntervalMs;
            }

            public void setMaxIntervalMs(long maxIntervalMs) {
                this.maxIntervalMs = Math.max(1_000, Math.min(300_000, maxIntervalMs));
            }
        }
    }
}
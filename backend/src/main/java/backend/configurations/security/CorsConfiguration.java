package backend.configurations.security;

import backend.configurations.environment.EnvironmentSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CorsConfiguration.class);

    /**
     * Headers the SPA actually sends. We enumerate them explicitly instead of allowing
     * `*` so that an attacker cannot use a custom header to hide payloads from CSRF
     * defences, and so {@code allowCredentials=true} stays compatible with the spec
     * (which forbids `*` headers in credentialled flows in modern browsers).
     */
    private static final List<String> ALLOWED_HEADERS = List.of(
            "Authorization",
            "Content-Type",
            "Accept",
            "X-Requested-With",
            "Idempotency-Key");

    private final EnvironmentSetting env;

    public CorsConfiguration(EnvironmentSetting env) {
        this.env = env;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> allowedOrigins = env.getCors().getAllowedOrigins();
        org.springframework.web.cors.CorsConfiguration config = new org.springframework.web.cors.CorsConfiguration();

        if (allowedOrigins.isEmpty()) {
            // No origins configured. Fail closed: an unconfigured deployment paired with
            // `allowCredentials=true` and a wildcard would be a credentialled-CORS bug
            // waiting to happen. Refuse all cross-origin requests until the operator
            // sets CORS_ALLOWED_ORIGINS.
            log.warn("CORS: no app.cors.allowed-origins configured — refusing all cross-origin requests");
            config.setAllowedOrigins(List.of());
        } else {
            config.setAllowedOrigins(allowedOrigins);
        }

        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(ALLOWED_HEADERS);
        config.setExposedHeaders(List.of("Authorization", "Location"));

        config.setAllowCredentials(true);
        config.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

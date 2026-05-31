package backend.configurations.auditing;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the current user id from the security context for JPA auditing
 * ({@code @CreatedBy} / {@code @LastModifiedBy}). The JWT filter installs a
 * {@code UUID} principal (user id); anonymous requests yield empty.
 */
@Component
public class SpringSecurityAuditorAware implements AuditorAware<UUID> {

    @Override
    public Optional<UUID> getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            return Optional.empty();
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof UUID uuid) {
            return Optional.of(uuid);
        }
        return Optional.empty();
    }
}

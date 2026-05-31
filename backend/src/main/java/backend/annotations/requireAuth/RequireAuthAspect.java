package backend.annotations.requireAuth;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Aspect
@Component
public class RequireAuthAspect {

    private static final String ROLE_PREFIX = "ROLE_";

    @Around("@annotation(requireAuth) || @within(requireAuth)")
    public Object enforceRequireAuth(ProceedingJoinPoint joinPoint, RequireAuth requireAuth) throws Throwable {
        RequireAuth effective = resolveRequireAuth(joinPoint, requireAuth);
        if (effective == null) {
            return joinPoint.proceed();
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }

        String[] roles = effective.roles();
        if (roles != null && roles.length > 0) {
            Set<String> allowed = Arrays.stream(roles)
                    .filter(r -> r != null && !r.isBlank())
                    .map(r -> r.startsWith(ROLE_PREFIX) ? r : ROLE_PREFIX + r)
                    .collect(Collectors.toSet());
            if (!allowed.isEmpty()) {
                Collection<String> userAuthorities = auth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toSet());
                boolean hasRole = userAuthorities.stream().anyMatch(allowed::contains);
                if (!hasRole) {
                    throw new AccessDeniedException("Insufficient role");
                }
            }
        }

        return joinPoint.proceed();
    }

    private static RequireAuth resolveRequireAuth(ProceedingJoinPoint joinPoint, RequireAuth requireAuth) {
        if (requireAuth != null) {
            return requireAuth;
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        RequireAuth onMethod = signature.getMethod().getAnnotation(RequireAuth.class);
        if (onMethod != null) {
            return onMethod;
        }
        RequireAuth onClass = signature.getMethod().getDeclaringClass().getAnnotation(RequireAuth.class);
        return onClass;
    }
}

package backend.configurations.security;

import backend.services.intf.auth.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtConfiguration extends OncePerRequestFilter {

    private final TokenService tokenService;

    public JwtConfiguration(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try {
            final String token = request.getHeader("Authorization");

            if (token != null && token.startsWith("Bearer ")) {
                String tokenWithoutBearer = token.substring(7);
                Authentication authentication = tokenService.getAuthentication(tokenWithoutBearer);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            // Invalid/expired token: clear context and continue so public routes (e.g. login) are not blocked.
            // Protected routes (@RequireAuth) will get 401 from method security.
            SecurityContextHolder.clearContext();
        }
        filterChain.doFilter(request, response);
    }
}

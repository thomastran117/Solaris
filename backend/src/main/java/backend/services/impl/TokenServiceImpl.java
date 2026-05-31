package backend.services.impl;

import backend.configurations.environment.EnvironmentSetting;
import backend.services.intf.CacheService;
import backend.services.intf.TokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.SecureRandom;
import java.util.*;
import java.util.function.Function;

@Service
public class TokenServiceImpl implements TokenService {

    private static final String REFRESH_TOKEN_PREFIX = "refresh:token:";
    private static final String REFRESH_USER_SET_PREFIX = "refresh:user:";
    private static final String PAYLOAD_SEP = ":";
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final EnvironmentSetting env;
    private final CacheService cache;
    private final SecureRandom rng = new SecureRandom();

    public TokenServiceImpl(EnvironmentSetting env, CacheService cache) {
        this.env = env;
        this.cache = cache;
    }

    @Override
    public int getUserIdFromToken(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Token is missing or improperly formatted");
        }
        String tokenWithoutBearer = token.substring(7);
        return Integer.parseInt(getClaimFromToken(tokenWithoutBearer, Claims::getSubject));
    }

    @Override
    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    @Override
    public Authentication getAuthentication(String token) {
        Claims claims = getAllClaimsFromToken(token);
        int userId = Integer.parseInt(claims.getSubject());
        String role = claims.get("role", String.class);

        Collection<GrantedAuthority> authorities = new ArrayList<>();
        if (role != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }

        return new UsernamePasswordAuthenticationToken(userId, null, authorities);
    }

    private Key getSigningKey() {
        String secret = env.getSecurity().getJwt().getSecret();
        byte[] keyBytes;

        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (IllegalArgumentException e) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }

        return Keys.hmacShaKeyFor(keyBytes);
    }

    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    @Override
    public String generateAccessToken(int userId, String role, String email) {
        long ttlMs = env.getSecurity().getJwt().getAccessTokenTtlSeconds() * 1000L;
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("role", role)
                .claim("email", email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ttlMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    @Override
    public String generateRefreshToken(int userId, String role, String email) {
        long ttlSeconds = env.getSecurity().getJwt().getRefreshTokenTtlSeconds();
        String tokenId = generateOpaqueToken();
        String payload = userId + PAYLOAD_SEP + (role != null ? role : "") + PAYLOAD_SEP + (email != null ? email : "");

        cache.set(REFRESH_TOKEN_PREFIX + tokenId, payload, ttlSeconds);
        cache.setAdd(REFRESH_USER_SET_PREFIX + userId, tokenId);

        return tokenId;
    }

    @Override
    public boolean validateRefreshToken(String token) {
        if (token == null || token.isBlank()) return false;
        return cache.exists(REFRESH_TOKEN_PREFIX + token);
    }

    @Override
    public RefreshTokenPayload getRefreshTokenPayload(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) return null;
        String raw = cache.get(REFRESH_TOKEN_PREFIX + refreshToken);
        if (raw == null) return null;
        return parsePayload(raw);
    }

    private RefreshTokenPayload parsePayload(String raw) {
        int sep1 = raw.indexOf(PAYLOAD_SEP);
        if (sep1 <= 0) return null;
        try {
            int userId = Integer.parseInt(raw.substring(0, sep1));
            int sep2 = raw.indexOf(PAYLOAD_SEP, sep1 + 1);
            String role;
            String email;
            if (sep2 > 0) {
                role = raw.substring(sep1 + 1, sep2);
                email = sep2 < raw.length() - 1 ? raw.substring(sep2 + 1) : "";
            } else {
                role = sep1 < raw.length() - 1 ? raw.substring(sep1 + 1) : "";
                email = "";
            }
            return new RefreshTokenPayload(userId, role, email);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String rotateRefreshToken(String oldToken) {
        String raw = cache.getAndDelete(REFRESH_TOKEN_PREFIX + oldToken);
        if (raw == null) return null;

        RefreshTokenPayload existing = parsePayload(raw);
        if (existing == null) return null;

        cache.setRemove(REFRESH_USER_SET_PREFIX + existing.userId(), oldToken);

        long ttlSeconds = env.getSecurity().getJwt().getRefreshTokenTtlSeconds();
        String newTokenId = generateOpaqueToken();
        String payload = existing.userId() + PAYLOAD_SEP + existing.role() + PAYLOAD_SEP + existing.email();

        cache.set(REFRESH_TOKEN_PREFIX + newTokenId, payload, ttlSeconds);
        cache.setAdd(REFRESH_USER_SET_PREFIX + existing.userId(), newTokenId);

        return newTokenId;
    }

    @Override
    public Map<String, Object> generateTokenPair(int userId, String role, String email) {
        String accessToken = generateAccessToken(userId, role, email);
        String refreshToken = generateRefreshToken(userId, role, email);

        Map<String, Object> tokens = new HashMap<>();
        tokens.put("accessToken", accessToken);
        tokens.put("refreshToken", refreshToken);
        tokens.put("tokenType", "Bearer");
        tokens.put("expiresIn", env.getSecurity().getJwt().getAccessTokenTtlSeconds());

        return tokens;
    }

    @Override
    public long getAccessTokenExpiresInSeconds() {
        return env.getSecurity().getJwt().getAccessTokenTtlSeconds();
    }

    @Override
    public void revokeRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) return;
        RefreshTokenPayload payload = getRefreshTokenPayload(refreshToken);
        cache.delete(REFRESH_TOKEN_PREFIX + refreshToken);
        if (payload != null) {
            cache.setRemove(REFRESH_USER_SET_PREFIX + payload.userId(), refreshToken);
        }
    }

    @Override
    public void revokeAllRefreshTokensForUser(int userId) {
        Set<String> tokenIds = cache.setMembers(REFRESH_USER_SET_PREFIX + userId);
        for (String tokenId : tokenIds) {
            cache.delete(REFRESH_TOKEN_PREFIX + tokenId);
        }
        cache.delete(REFRESH_USER_SET_PREFIX + userId);
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        rng.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

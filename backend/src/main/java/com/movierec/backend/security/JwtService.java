package com.movierec.backend.security;

import com.movierec.backend.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Issues and validates HS256 JWTs used to authenticate requests. The token's subject is the
 * user's username; {@code role} is included as an informational claim, but authorization
 * decisions are always made against a freshly-loaded {@link User} (see
 * {@link JwtAuthenticationFilter}) rather than trusting the claim, so a role change takes effect
 * immediately instead of waiting for the token to expire.
 *
 * <p><b>Security-sensitive:</b> {@code jwt.secret} must be a real, random, environment-provided
 * value (at least 32 bytes) outside local development.
 */
@Component
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret, @Value("${jwt.expiration-ms}") long expirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(signingKey)
                .compact();
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    /**
     * Parses and validates {@code token}'s signature and expiry, returning its username subject.
     * Throws an unchecked {@link io.jsonwebtoken.JwtException} (caught by
     * {@link JwtAuthenticationFilter}) if the token is malformed, expired, or has an invalid
     * signature.
     */
    public String extractUsername(String token) {
        Claims claims = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
        return claims.getSubject();
    }
}

package com.loanorigination.loanservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

// Utility bean for creating, validating, and parsing JWTs.
// Used by AuthService (to issue tokens on login) and JwtFilter (to validate incoming requests).
@Component
public class JwtUtil {

    // SecretKey is the cryptographic key used to sign and verify tokens.
    // It is derived from the plain-text jwt.secret in application.yml.
    private final SecretKey secretKey;

    // How long (in milliseconds) a token stays valid after it is issued.
    // Configured via jwt.expiration-ms in application.yml (default: 86400000 = 24 hours).
    private final long expirationMs;

    // @Value pulls the two JWT settings from application.yml at startup.
    // Keys.hmacShaKeyFor converts the raw string secret into a javax.crypto.SecretKey
    // using HMAC-SHA (the algorithm family used by jjwt to sign tokens).
    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    // Builds and signs a JWT for the given user.
    // Token structure (claims):
    //   sub  → userId (e.g. "42")         — standard "subject" claim, identifies the user
    //   role → role name (e.g. "ADMIN")   — custom claim, read by api-gateway to set X-User-Role header
    //   iat  → issued-at timestamp        — when the token was created
    //   exp  → expiration timestamp       — iat + expirationMs
    // .compact() serializes the token into the standard Base64URL header.payload.signature string.
    public String generateToken(Long userId, String role) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(secretKey)
                .compact();
    }

    // Returns true only if the token has a valid signature AND is not expired.
    // parseSignedClaims() does both checks internally.
    // JwtException covers tampered/malformed tokens; IllegalArgumentException covers null/blank input.
    // Returning false (rather than throwing) lets the filter reject the request cleanly with a 401.
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // Reads the "sub" claim, which holds the userId as a string, and converts it back to Long.
    public Long extractUserId(String token) {
        return Long.parseLong(getClaims(token).getSubject());
    }

    // Reads the custom "role" claim added in generateToken().
    public String extractRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    // Parses and verifies the token signature, then returns the payload (all claims).
    // Private because callers should use extractUserId / extractRole, not raw claims.
    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

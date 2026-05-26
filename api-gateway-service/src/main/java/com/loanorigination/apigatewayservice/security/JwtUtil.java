package com.loanorigination.apigatewayservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

// Validation-only JWT utility for the api-gateway.
// Validates tokens issued by auth-service and extracts userId/role.
// Does NOT generate tokens — that's only done by auth-service.
@Component
public class JwtUtil {

    private final SecretKey secretKey;

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // Returns true if token is valid (good signature, not expired).
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // Extracts the userId ("sub" claim) from the token.
    public Long extractUserId(String token) {
        return Long.parseLong(getClaims(token).getSubject());
    }

    // Extracts the role (custom "role" claim) from the token.
    public String extractRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    // Internal helper: parses and verifies the token, returns all claims.
    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

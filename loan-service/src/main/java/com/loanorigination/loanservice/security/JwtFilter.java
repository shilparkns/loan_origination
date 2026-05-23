package com.loanorigination.loanservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Intercepts every HTTP request and validates the JWT in the Authorization header.
// If valid, extracts userId and role, then sets them as X-User-Id and X-User-Role headers
// for downstream services (especially the api-gateway, which forwards them to other microservices).
// If invalid or missing, passes the request through without the headers.
// Public endpoints (/auth/login, /auth/register) can be accessed without a token.
@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    // Explicit @Autowired constructor instead of @RequiredArgsConstructor.
    // OncePerRequestFilter is a Spring framework class that has its own constructors,
    // and Lombok-generated constructors can conflict with that, causing initialization errors.
    @Autowired
    public JwtFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            // Extract token from the Authorization header (expected format: "Bearer <token>").
            String token = extractToken(request);

            // If a token was provided and is valid, extract userId and role
            // and set them as headers for downstream services.
            if (token != null && jwtUtil.validateToken(token)) {
                Long userId = jwtUtil.extractUserId(token);
                String role = jwtUtil.extractRole(token);

                // Set custom headers that downstream services trust instead of re-validating the JWT.
                request.setAttribute("X-User-Id", userId);
                request.setAttribute("X-User-Role", role);
            }
        } catch (Exception e) {
            // If anything goes wrong parsing the token, log it and continue.
            // The request will proceed without userId/role headers.
            logger.debug("JWT validation failed: " + e.getMessage());
        }

        // Pass the request and response to the next filter in the chain.
        filterChain.doFilter(request, response);
    }

    // Extracts the JWT from the Authorization header.
    // Expected format: "Bearer <token>"
    // Returns null if the header is missing or malformed.
    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring("Bearer ".length());
        }
        return null;
    }
}

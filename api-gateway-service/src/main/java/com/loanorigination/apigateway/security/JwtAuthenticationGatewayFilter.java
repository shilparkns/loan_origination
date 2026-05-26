package com.loanorigination.apigateway.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

// Global filter that validates JWT tokens on every request.
// Validates tokens and adds X-User-Id and X-User-Role headers for downstream services.
// Skips validation for /auth/** routes (public endpoints).
@Component
public class JwtAuthenticationGatewayFilter implements GlobalFilter {

    private final JwtUtil jwtUtil;

    @Autowired
    public JwtAuthenticationGatewayFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Skip authentication for /auth/** routes (public endpoints: login, register, etc.)
        if (path.startsWith("/auth/")) {
            return chain.filter(exchange);
        }

        // Extract Authorization header
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

        // Missing token → 401 Unauthorized
        if (authHeader == null || authHeader.isEmpty()) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // Extract token from "Bearer <token>" format
        String token = authHeader.startsWith("Bearer ")
            ? authHeader.substring(7)
            : authHeader;

        // Validate token
        if (!jwtUtil.validateToken(token)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // Extract userId and role from token
        Long userId = jwtUtil.extractUserId(token);
        String role = jwtUtil.extractRole(token);

        // Mutate the request to add X-User-Id and X-User-Role headers
        // These headers will be available to downstream services (loan-service, etc.)
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
            .header("X-User-Id", userId.toString())
            .header("X-User-Role", role)
            .build();

        // Create a new exchange with the mutated request and continue the filter chain
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }
}

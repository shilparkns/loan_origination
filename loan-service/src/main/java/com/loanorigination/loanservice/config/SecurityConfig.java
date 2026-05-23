package com.loanorigination.loanservice.config;

import com.loanorigination.loanservice.security.JwtFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// Central configuration for Spring Security.
// Defines which endpoints are public, which require authentication,
// password encoding, and registers the JwtFilter in the security chain.
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    @Autowired
    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    // Configures HTTP security: authentication rules, filter chain, session policy.
    // httpSecurity.authorizeHttpRequests() — whitelist public endpoints, protect others.
    // httpSecurity.sessionManagement().sessionCreationPolicy(STATELESS) — don't create server-side sessions.
    // jwtFilter is registered BEFORE UsernamePasswordAuthenticationFilter
    // so it runs first and can set the authentication headers for downstream code.
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                .csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers("/auth/login", "/auth/register").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return httpSecurity.build();
    }

    // Password encoder bean used by AuthService when hashing user passwords.
    // BCrypt is a key-derivation function designed for passwords:
    // it is slow (deliberately) and salted, making brute-force attacks impractical.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

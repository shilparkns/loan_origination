package com.loanorigination.authservice.controller;

import com.loanorigination.authservice.dto.LoginRequest;
import com.loanorigination.authservice.dto.LoginResponse;
import com.loanorigination.authservice.dto.RegisterRequest;
import com.loanorigination.authservice.dto.UserDto;
import com.loanorigination.authservice.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

// HTTP endpoints for user registration and login.
// These are public endpoints (defined in SecurityConfig as permitAll).
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    @Autowired
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // POST /auth/register — Create a new user account.
    // @Valid ensures the RegisterRequest passes validation (non-null fields, valid
    // email, etc.).
    // If validation fails, MethodArgumentNotValidException is thrown and caught by
    // GlobalExceptionHandler.
    // Returns 201 Created with a JWT token in the LoginResponse.
    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        LoginResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // POST /auth/login — Authenticate a user and issue a JWT.
    // @Valid ensures the LoginRequest has non-null email and password, and valid
    // email format.
    // If validation fails, MethodArgumentNotValidException is thrown and caught by
    // GlobalExceptionHandler.
    // Returns 200 OK with the JWT token in the LoginResponse.
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    // GET /auth/users/{userId} — Fetch user details by ID for service-to-service
    // calls.
    // Returns 200 OK with user details (id, firstName, lastName, email, role).
    // Note: Password is not included in the response for security.
    @GetMapping("/users/{userId}")
    public ResponseEntity<UserDto> getUserById(@PathVariable Long userId) {
        UserDto userDto = authService.getUserById(userId);
        return ResponseEntity.ok(userDto);
    }
}

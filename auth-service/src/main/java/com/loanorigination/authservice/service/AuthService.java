package com.loanorigination.authservice.service;

import com.loanorigination.authservice.dto.LoginRequest;
import com.loanorigination.authservice.dto.LoginResponse;
import com.loanorigination.authservice.dto.RegisterRequest;
import com.loanorigination.authservice.dto.UserDto;
import com.loanorigination.authservice.entity.User;
import com.loanorigination.authservice.repository.UserRepository;
import com.loanorigination.authservice.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

// Business logic for user registration and authentication.
// Uses JwtUtil to generate tokens and PasswordEncoder to hash passwords securely.
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public AuthService(UserRepository userRepository, JwtUtil jwtUtil, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    // Creates a new user account.
    // Validates that email is not already taken, hashes the password, saves to DB.
    // Returns a LoginResponse with a fresh JWT token so the user can immediately
    // use their account.
    public LoginResponse register(RegisterRequest request) {
        // Check if email already exists.
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }

        // Hash the password. If someone steals the database,
        // they cannot reverse-engineer the plaintext password from the hash.
        String hashedPassword = passwordEncoder.encode(request.getPassword());

        // Create and save the new user.
        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(hashedPassword)
                .role(request.getRole())
                .build();

        User savedUser = userRepository.save(user);

        // Issue a JWT so the client can use it for subsequent requests.
        String token = jwtUtil.generateToken(savedUser.getId(), savedUser.getRole().toString());

        return new LoginResponse(token);
    }

    // Authenticates a user: validates email exists, password matches, and issues a
    // JWT.
    // If email doesn't exist or password is wrong, throws an exception.
    public LoginResponse login(LoginRequest request) {
        // Look up the user by email.
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        // Compare the provided password (plaintext) against the stored hash.
        // passwordEncoder.matches() hashes the input and compares it to the stored
        // hash.
        // This is one-way: we never store or transmit the plaintext password.
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        // Password matches. Issue a JWT.
        String token = jwtUtil.generateToken(user.getId(), user.getRole().toString());

        return new LoginResponse(token);
    }

    // Fetches user details by ID for service-to-service calls.
    // Converts User entity to UserDto to avoid exposing password field.
    public UserDto getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return new UserDto(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole());
    }
}

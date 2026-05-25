package com.loanorigination.loanservice.client;

import com.loanorigination.loanservice.dto.UserDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

// HTTP client that fetches user data from auth-service.
// Loan-service calls this when it needs user email or role (e.g., for audit logs).
// Auth-service is the source of truth for user data.
@Service
public class UserClient {

    @Autowired
    private RestTemplate restTemplate;

    private static final String AUTH_SERVICE_URL = "http://localhost:8083";

    // Fetches a user by ID from auth-service.
    // Makes GET request to auth-service /auth/users/{userId} and returns UserDto.
    public UserDto getUserById(Long userId) {
        String url = AUTH_SERVICE_URL + "/auth/users/" + userId;
        return restTemplate.getForObject(url, UserDto.class);
    }
}

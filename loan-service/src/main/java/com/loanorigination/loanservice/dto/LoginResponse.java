package com.loanorigination.loanservice.dto;

// Response payload for POST /auth/login and POST /auth/register.
// Contains the JWT token that the client will use in the Authorization header
// for subsequent requests.
public class LoginResponse {

    private String token;

    // Constructors
    public LoginResponse() {
    }

    public LoginResponse(String token) {
        this.token = token;
    }

    // Getters and Setters
    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}

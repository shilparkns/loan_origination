package com.loanorigination.loanservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Response payload for POST /auth/login and POST /auth/register.
// Contains the JWT token that the client will use in the Authorization header
// for subsequent requests.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String token;
}

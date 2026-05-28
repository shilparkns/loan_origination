package com.loanorigination.testclient.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@RestController
@RequestMapping("/test")
public class TestGatewayController {

    private final RestClient gatewayRestClient;

    public TestGatewayController(RestClient gatewayRestClient) {
        this.gatewayRestClient = gatewayRestClient;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "service", "test-client");
    }

    @PostMapping("/auth/register")
    public ResponseEntity<String> register(@RequestBody Map<String, Object> payload) {
        return forward(HttpMethod.POST, "/auth/register", payload, null);
    }

    @PostMapping("/auth/login")
    public ResponseEntity<String> login(@RequestBody Map<String, Object> payload) {
        return forward(HttpMethod.POST, "/auth/login", payload, null);
    }

    @PostMapping("/loans")
    public ResponseEntity<String> createLoan(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody Map<String, Object> payload
    ) {
        return forward(HttpMethod.POST, "/api/loans", payload, authorization);
    }

    @GetMapping("/loans")
    public ResponseEntity<String> getLoans(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization
    ) {
        return forward(HttpMethod.GET, "/api/loans", null, authorization);
    }

    @GetMapping("/loans/{id}")
    public ResponseEntity<String> getLoanById(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long id
    ) {
        return forward(HttpMethod.GET, "/api/loans/" + id, null, authorization);
    }

    @PatchMapping("/loans/{id}/status")
    public ResponseEntity<String> updateLoanStatus(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload
    ) {
        return forward(HttpMethod.PATCH, "/api/loans/" + id + "/status", payload, authorization);
    }

    @PostMapping("/loans/{id}/assessment")
    public ResponseEntity<String> submitAssessment(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload
    ) {
        return forward(HttpMethod.POST, "/api/loans/" + id + "/assessment", payload, authorization);
    }

    @PostMapping("/loans/{id}/decision")
    public ResponseEntity<String> submitDecision(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload
    ) {
        return forward(HttpMethod.POST, "/api/loans/" + id + "/decision", payload, authorization);
    }

    @PostMapping("/loans/{id}/documents")
    public ResponseEntity<String> uploadDocument(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload
    ) {
        return forward(HttpMethod.POST, "/api/loans/" + id + "/documents", payload, authorization);
    }

    @PatchMapping("/loans/{id}/disburse")
    public ResponseEntity<String> disburse(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long id
    ) {
        return forward(HttpMethod.PATCH, "/api/loans/" + id + "/disburse", null, authorization);
    }

    private ResponseEntity<String> forward(
            HttpMethod method,
            String path,
            Map<String, Object> payload,
            String authorization
    ) {
        try {
            RestClient.RequestBodyUriSpec request = gatewayRestClient.method(method);
            RestClient.RequestBodySpec requestSpec = request
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON);

            if (authorization != null && !authorization.isBlank()) {
                requestSpec = requestSpec.header(HttpHeaders.AUTHORIZATION, authorization);
            }

            ResponseEntity<String> upstream;
            if (payload != null) {
                upstream = requestSpec.body(payload)
                        .retrieve()
                        .toEntity(String.class);
            } else {
                upstream = requestSpec
                        .retrieve()
                        .toEntity(String.class);
            }

            // Do not forward hop-by-hop headers (e.g. Transfer-Encoding: chunked) from gateway;
            // that breaks curl and scripts when Tomcat re-encodes the body.
            return ResponseEntity.status(upstream.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(upstream.getBody());
        } catch (RestClientResponseException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ex.getResponseBodyAsString());
        }
    }
}

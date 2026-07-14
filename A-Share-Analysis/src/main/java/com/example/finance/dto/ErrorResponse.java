package com.example.finance.dto;

import java.time.Instant;

/**
 * Structured error body returned to the frontend when a request fails
 * validation, so the client can distinguish "bad input" (400) from
 * "server problem" (500) and show an appropriate message.
 */
public class ErrorResponse {

    private final String message;
    private final Instant timestamp;

    public ErrorResponse(String message) {
        this.message = message;
        this.timestamp = Instant.now();
    }

    public String getMessage() { return message; }
    public Instant getTimestamp() { return timestamp; }
}
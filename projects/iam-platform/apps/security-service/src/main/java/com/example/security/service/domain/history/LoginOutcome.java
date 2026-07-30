package com.example.security.service.domain.history;

public enum LoginOutcome {
    ATTEMPTED,
    SUCCESS,
    FAILURE,
    RATE_LIMITED,
    TOKEN_REUSE,
    REFRESH
}

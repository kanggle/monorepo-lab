package com.example.auth.domain.service;

public interface SessionProperties {
    int maxConcurrentSessions();
    long inactivityTimeoutSeconds();
}

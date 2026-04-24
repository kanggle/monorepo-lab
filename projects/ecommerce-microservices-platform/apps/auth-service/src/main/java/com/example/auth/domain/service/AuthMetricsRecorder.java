package com.example.auth.domain.service;

public interface AuthMetricsRecorder {

    void incrementSignup();

    void incrementLoginSuccess();

    void incrementLoginFailure(String reason);

    void incrementLogout();

    void incrementTokenRefreshSuccess();

    void incrementTokenRefreshFailure();

    void incrementSessionEviction();

    void incrementEventPublishFailure(String eventType);
}

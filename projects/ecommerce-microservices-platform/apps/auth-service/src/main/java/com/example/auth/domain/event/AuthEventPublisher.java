package com.example.auth.domain.event;

public interface AuthEventPublisher {
    void publish(AuthEvent event);
}

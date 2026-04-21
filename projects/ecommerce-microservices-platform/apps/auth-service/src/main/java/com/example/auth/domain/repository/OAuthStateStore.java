package com.example.auth.domain.repository;

import java.time.Duration;
import java.util.Optional;

public interface OAuthStateStore {

    void save(String state, String callbackUrl, Duration ttl);

    Optional<String> getAndDelete(String state);
}

package com.example.auth.application.dto;

import java.time.Instant;
import java.util.UUID;

public record SignupResult(
    UUID userId,
    String email,
    String name,
    Instant createdAt
) {}

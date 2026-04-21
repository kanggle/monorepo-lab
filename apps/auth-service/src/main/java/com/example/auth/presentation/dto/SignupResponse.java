package com.example.auth.presentation.dto;

import java.time.Instant;
import java.util.UUID;

public record SignupResponse(
    UUID userId,
    String email,
    String name,
    Instant createdAt
) {}

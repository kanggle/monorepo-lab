package com.example.auth.domain.event;

import java.util.UUID;

public record UserSignedUp(
    UUID userId,
    String email,
    String name
) {}

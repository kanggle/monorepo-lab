package com.example.auth.domain.service;

import java.util.UUID;

public record ParsedToken(UUID userId, String email) {}

package com.example.auth.application.dto;

public record LoginCommand(
    String email,
    String password,
    String ipAddress,
    String userAgent
) {}

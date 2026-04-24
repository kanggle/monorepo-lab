package com.example.auth.application.dto;

public record SignupCommand(
    String email,
    String password,
    String name,
    String ipAddress,
    String userAgent
) {}

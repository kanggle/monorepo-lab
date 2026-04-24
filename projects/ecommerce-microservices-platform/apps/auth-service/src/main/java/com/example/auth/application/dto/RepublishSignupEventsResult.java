package com.example.auth.application.dto;

public record RepublishSignupEventsResult(
    int totalUsers,
    int publishedCount,
    int failedCount
) {}

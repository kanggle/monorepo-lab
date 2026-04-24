package com.example.auth.presentation.dto;

public record RepublishSignupEventsResponse(
    int totalUsers,
    int publishedCount,
    int failedCount
) {}

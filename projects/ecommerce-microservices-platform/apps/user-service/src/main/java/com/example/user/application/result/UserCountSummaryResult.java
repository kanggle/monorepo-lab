package com.example.user.application.result;

public record UserCountSummaryResult(
        long today,
        long week,
        long month,
        long total
) {}

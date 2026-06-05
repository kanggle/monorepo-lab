package com.example.admin.presentation.dto;

public record UnlockAccountRequest(
        String reason,
        String ticketId
) {}

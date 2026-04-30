package com.example.admin.presentation.dto;

public record LockAccountRequest(
        String reason,
        String ticketId
) {}

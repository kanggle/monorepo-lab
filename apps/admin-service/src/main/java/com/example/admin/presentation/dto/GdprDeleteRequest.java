package com.example.admin.presentation.dto;

public record GdprDeleteRequest(
        String reason,
        String ticketId
) {}

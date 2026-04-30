package com.example.auth.presentation.dto;

import com.example.auth.application.result.ListSessionsResult;

import java.util.List;

public record ListSessionsResponse(
        List<DeviceSessionResponse> items,
        int total,
        int maxActiveSessions
) {
    public static ListSessionsResponse from(ListSessionsResult r) {
        return new ListSessionsResponse(
                r.items().stream().map(DeviceSessionResponse::from).toList(),
                r.total(),
                r.maxActiveSessions());
    }
}

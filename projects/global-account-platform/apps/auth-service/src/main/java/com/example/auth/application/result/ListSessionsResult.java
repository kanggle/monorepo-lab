package com.example.auth.application.result;

import java.util.List;

public record ListSessionsResult(List<DeviceSessionResult> items, int total, int maxActiveSessions) {
    public static ListSessionsResult of(List<DeviceSessionResult> items, int max) {
        return new ListSessionsResult(items, items.size(), max);
    }
}

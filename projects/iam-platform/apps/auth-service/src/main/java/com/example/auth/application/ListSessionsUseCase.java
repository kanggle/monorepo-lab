package com.example.auth.application;

import com.example.auth.application.result.DeviceSessionResult;
import com.example.auth.application.result.ListSessionsResult;
import com.example.auth.domain.repository.DeviceSessionRepository;
import com.example.auth.domain.session.DeviceSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ListSessionsUseCase {

    private final DeviceSessionRepository deviceSessionRepository;

    @Value("${auth.device-session.max-active-sessions:10}")
    private int maxActiveSessions;

    @Transactional(readOnly = true)
    public ListSessionsResult execute(String accountId, String currentDeviceId) {
        List<DeviceSession> sessions = deviceSessionRepository.findActiveByAccountId(accountId);
        List<DeviceSessionResult> items = sessions.stream()
                .map(s -> DeviceSessionResult.of(s, Objects.equals(s.getDeviceId(), currentDeviceId)))
                .toList();
        return ListSessionsResult.of(items, maxActiveSessions);
    }
}

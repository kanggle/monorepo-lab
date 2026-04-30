package com.example.auth.application;

import com.example.auth.application.exception.SessionNotFoundException;
import com.example.auth.application.exception.SessionOwnershipMismatchException;
import com.example.auth.application.result.DeviceSessionResult;
import com.example.auth.domain.repository.DeviceSessionRepository;
import com.example.auth.domain.session.DeviceSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetCurrentSessionUseCase {

    private final DeviceSessionRepository deviceSessionRepository;

    @Transactional(readOnly = true)
    public DeviceSessionResult execute(String accountId, String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            throw new SessionNotFoundException();
        }
        DeviceSession session = deviceSessionRepository.findByDeviceId(deviceId)
                .orElseThrow(SessionNotFoundException::new);
        if (!session.getAccountId().equals(accountId)) {
            // Treat cross-account lookups as not-found to avoid leaking ownership info.
            throw new SessionOwnershipMismatchException();
        }
        if (session.isRevoked()) {
            throw new SessionNotFoundException();
        }
        return DeviceSessionResult.of(session, true);
    }
}

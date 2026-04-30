package com.example.security.application;

import com.example.security.application.event.SecurityEventPublisher;
import com.example.security.domain.detection.AccountLockClient;
import com.example.security.domain.suspicious.SuspiciousEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class IssueAutoLockCommandUseCase {

    private final AccountLockClient accountLockClient;
    private final SuspiciousEventPersistenceService persistenceService;
    private final SecurityEventPublisher publisher;

    public void execute(SuspiciousEvent event) {
        AccountLockClient.LockResult result = accountLockClient.lock(event);
        String code = switch (result.status()) {
            case SUCCESS -> "SUCCESS";
            case ALREADY_LOCKED -> "ALREADY_LOCKED";
            case INVALID_TRANSITION, FAILURE -> "FAILURE";
        };
        SuspiciousEvent updated = event.withLockRequestResult(code);
        persistenceService.updateLockResult(updated);
        publisher.publishAutoLockTriggered(updated, result.status());

        if (result.status() == AccountLockClient.Status.FAILURE) {
            publisher.publishAutoLockPending(updated);
            log.warn("Auto-lock FAILURE — emitted pending event; suspiciousEventId={}, accountId={}",
                    updated.getId(), updated.getAccountId());
        }
    }
}

package com.example.security.application;

import com.example.security.domain.detection.DetectionResult;
import com.example.security.domain.detection.EvaluationContext;
import com.example.security.domain.detection.RiskLevel;
import com.example.security.domain.detection.RiskScoreAggregator;
import com.example.security.domain.repository.SuspiciousEventRepository;
import com.example.security.domain.suspicious.SuspiciousEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistence boundary for suspicious events. Extracted to a separate Spring bean
 * so that {@link Transactional} methods are invoked through the AOP proxy
 * (external invocation) rather than via self-invocation from
 * {@link DetectSuspiciousActivityUseCase}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuspiciousEventPersistenceService {

    private final SuspiciousEventRepository suspiciousEventRepository;

    @Transactional
    public SuspiciousEvent recordSuspiciousEvent(EvaluationContext ctx,
                                                 RiskScoreAggregator.Aggregated aggregated,
                                                 RiskLevel level) {
        DetectionResult winner = aggregated.winner();
        SuspiciousEvent event = SuspiciousEvent.create(
                UUID.randomUUID().toString(),
                ctx.accountId(),
                winner.ruleCode(),
                winner.riskScore(),
                level,
                winner.evidence(),
                ctx.eventId(),
                Instant.now()
        );
        suspiciousEventRepository.save(event);
        log.info("Persisted suspicious event: id={}, accountId={}, ruleCode={}, score={}, action={}",
                event.getId(), event.getAccountId(), event.getRuleCode(), event.getRiskScore(), level);
        return event;
    }

    @Transactional
    public void updateLockResult(SuspiciousEvent event) {
        suspiciousEventRepository.save(event);
    }
}

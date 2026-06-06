package com.example.security.application;

import com.example.security.application.event.SecurityEventPublisher;
import com.example.security.domain.detection.DetectionResult;
import com.example.security.domain.detection.EvaluationContext;
import com.example.security.domain.detection.RiskLevel;
import com.example.security.domain.detection.RiskScoreAggregator;
import com.example.security.domain.detection.SuspiciousActivityRule;
import com.example.security.domain.suspicious.SuspiciousEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the detection pipeline for a single consumed auth event:
 * <ol>
 *   <li>Evaluate every registered {@link SuspiciousActivityRule} in order.</li>
 *   <li>Aggregate results (max score wins).</li>
 *   <li>Decide {@link RiskLevel} action:
 *     <ul>
 *       <li>NONE → no-op</li>
 *       <li>ALERT → persist suspicious_events + publish {@code suspicious.detected}</li>
 *       <li>AUTO_LOCK → persist + publish + call account-service lock</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>Persistence is delegated to {@link SuspiciousEventPersistenceService} so that
 * {@link org.springframework.transaction.annotation.Transactional} boundaries are
 * enforced by the Spring AOP proxy (external call) instead of self-invocation.</p>
 */
@Slf4j
@Service
public class DetectSuspiciousActivityUseCase {

    private final List<SuspiciousActivityRule> rules;
    private final SuspiciousEventPersistenceService persistenceService;
    private final SecurityEventPublisher publisher;
    private final IssueAutoLockCommandUseCase issueAutoLockCommandUseCase;

    public DetectSuspiciousActivityUseCase(List<SuspiciousActivityRule> rules,
                                            SuspiciousEventPersistenceService persistenceService,
                                            SecurityEventPublisher publisher,
                                            IssueAutoLockCommandUseCase issueAutoLockCommandUseCase) {
        this.rules = List.copyOf(rules);
        this.persistenceService = persistenceService;
        this.publisher = publisher;
        this.issueAutoLockCommandUseCase = issueAutoLockCommandUseCase;
    }

    /**
     * Entry point — runs the pipeline and returns the persisted SuspiciousEvent,
     * or null if no rule fired above the NONE threshold.
     */
    public SuspiciousEvent detect(EvaluationContext ctx) {
        if (ctx == null || !ctx.hasAccount()) {
            return null;
        }
        List<DetectionResult> results = new ArrayList<>(rules.size());
        for (SuspiciousActivityRule rule : rules) {
            try {
                DetectionResult r = rule.evaluate(ctx);
                results.add(r == null ? DetectionResult.NONE : r);
            } catch (RuntimeException e) {
                log.warn("Rule {} threw; treating as NONE for eventId={}", rule.ruleCode(), ctx.eventId(), e);
                results.add(DetectionResult.NONE);
            }
        }
        RiskScoreAggregator.Aggregated aggregated = RiskScoreAggregator.aggregate(results);
        if (!aggregated.anyFired()) {
            return null;
        }
        RiskLevel level = aggregated.level();
        if (level == RiskLevel.NONE) {
            return null;
        }

        SuspiciousEvent persisted = persistenceService.recordSuspiciousEvent(ctx, aggregated, level);
        publisher.publishSuspiciousDetected(persisted);

        if (level == RiskLevel.AUTO_LOCK) {
            issueAutoLockCommandUseCase.execute(persisted);
        }
        return persisted;
    }
}

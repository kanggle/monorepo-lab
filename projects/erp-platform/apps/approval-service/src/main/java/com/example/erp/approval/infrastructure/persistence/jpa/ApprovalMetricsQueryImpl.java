package com.example.erp.approval.infrastructure.persistence.jpa;

import com.example.erp.approval.domain.request.ApprovalStatus;
import com.example.erp.approval.domain.request.repository.ApprovalMetricsQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** JPA adapter for {@link ApprovalMetricsQuery} (TASK-MONO-809). */
@Component
@RequiredArgsConstructor
public class ApprovalMetricsQueryImpl implements ApprovalMetricsQuery {

    private final ApprovalMetricsJpaRepository jpa;

    @Override
    public List<Instant> submittedAt(String tenantId, Instant from, Instant to) {
        return jpa.submittedAt(tenantId, from, to);
    }

    @Override
    public List<Instant> submittedAtByParticipant(String tenantId, String participantId,
                                                  Instant from, Instant to) {
        return jpa.submittedAtByParticipant(tenantId, participantId, from, to);
    }

    @Override
    public List<Instant> finalizedAt(String tenantId, ApprovalStatus status,
                                     Instant from, Instant to) {
        return jpa.finalizedAt(tenantId, status, from, to);
    }

    @Override
    public List<Instant> finalizedAtByParticipant(String tenantId, String participantId,
                                                  ApprovalStatus status, Instant from, Instant to) {
        return jpa.finalizedAtByParticipant(tenantId, participantId, status, from, to);
    }
}

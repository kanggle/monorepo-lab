package com.example.erp.approval.application;

import com.example.common.metrics.PerformanceMetrics;
import com.example.common.metrics.PerformanceMetrics.Metric;
import com.example.common.time.KstPeriodBounds;
import com.example.common.time.KstWindow;
import com.example.common.time.MetricsPeriod;
import com.example.erp.approval.application.port.outbound.AuthorizationPort;
import com.example.erp.approval.application.port.outbound.MasterDataPort;
import com.example.erp.approval.domain.authorization.AuthorizationDecision;
import com.example.erp.approval.domain.authorization.RequiredScope;
import com.example.erp.approval.domain.error.ApprovalErrors.DataScopeForbiddenException;
import com.example.erp.approval.domain.error.ApprovalErrors.PermissionDeniedException;
import com.example.erp.approval.domain.request.ApprovalStatus;
import com.example.erp.approval.domain.request.repository.ApprovalMetricsQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Overview performance metrics (TASK-MONO-809, ADR-MONO-084 P4) — see
 * {@code approval-api.md} § GET /api/erp/approval/metrics. Counts <b>requests</b> by their
 * own transition timestamp: {@code submitted_at}, and {@code finalized_at} split by the final
 * status. Never counts {@code approval_action} rows (an intermediate-stage approval writes one
 * but does not finalize) and never counts withdrawals.
 *
 * <p>Authorization and scope are the list's: {@code authorizeRead}; operator / platform scope →
 * whole tenant, otherwise the caller's linked employee as submitter OR approver, unlinked → zeros.
 */
@Service
@RequiredArgsConstructor
public class ApprovalMetricsService {

    public static final String METRIC_SUBMITTED = "submitted";
    public static final String METRIC_APPROVED = "approved";
    public static final String METRIC_REJECTED = "rejected";

    private final ApprovalMetricsQuery metricsQuery;
    private final MasterDataPort masterDataPort;
    private final AuthorizationPort authorizationPort;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PerformanceMetrics getMetrics(ActorContext actor, MetricsPeriod period) {
        authorizeRead(actor);
        KstPeriodBounds bounds = KstPeriodBounds.from(clock);

        // Scope — copied from ApprovalApplicationService#list.
        Optional<String> participant = Optional.empty();
        boolean tenantWide = actor.isOperator() || actor.isPlatformScope();
        if (!tenantWide) {
            participant = ActingEmployee.forRead(masterDataPort, actor);
            if (participant.isEmpty()) {
                // Unlinked caller participates in nothing → zeros, no repository call.
                return PerformanceMetrics.of(bounds, period, List.of(
                        Metric.count(METRIC_SUBMITTED, 0, 0),
                        Metric.count(METRIC_APPROVED, 0, 0),
                        Metric.count(METRIC_REJECTED, 0, 0)), Map.of());
            }
        }
        String tenantId = actor.tenantId();
        String me = participant.orElse(null);

        KstWindow current = bounds.currentWindow(period);
        KstWindow previous = bounds.previousWindow(period);

        List<Instant> submittedNow = submitted(tenantId, me, current);
        List<Instant> approvedNow = finalized(tenantId, me, ApprovalStatus.APPROVED, current);
        List<Instant> rejectedNow = finalized(tenantId, me, ApprovalStatus.REJECTED, current);

        Map<LocalDate, Map<String, Long>> daily = new HashMap<>();
        bucket(daily, METRIC_SUBMITTED, submittedNow);
        bucket(daily, METRIC_APPROVED, approvedNow);
        bucket(daily, METRIC_REJECTED, rejectedNow);

        return PerformanceMetrics.of(bounds, period, List.of(
                Metric.count(METRIC_SUBMITTED, submittedNow.size(),
                        submitted(tenantId, me, previous).size()),
                Metric.count(METRIC_APPROVED, approvedNow.size(),
                        finalized(tenantId, me, ApprovalStatus.APPROVED, previous).size()),
                Metric.count(METRIC_REJECTED, rejectedNow.size(),
                        finalized(tenantId, me, ApprovalStatus.REJECTED, previous).size())),
                daily);
    }

    private List<Instant> submitted(String tenantId, String me, KstWindow w) {
        return me == null
                ? metricsQuery.submittedAt(tenantId, w.fromInstant(), w.toInstant())
                : metricsQuery.submittedAtByParticipant(tenantId, me, w.fromInstant(), w.toInstant());
    }

    private List<Instant> finalized(String tenantId, String me, ApprovalStatus status, KstWindow w) {
        return me == null
                ? metricsQuery.finalizedAt(tenantId, status, w.fromInstant(), w.toInstant())
                : metricsQuery.finalizedAtByParticipant(
                        tenantId, me, status, w.fromInstant(), w.toInstant());
    }

    private static void bucket(Map<LocalDate, Map<String, Long>> daily, String key,
                               List<Instant> instants) {
        for (Instant at : instants) {
            daily.computeIfAbsent(KstPeriodBounds.dateOf(at), d -> new HashMap<>())
                    .merge(key, 1L, Long::sum);
        }
    }

    /** READ gate — identical to {@link ApprovalApplicationService}'s {@code authorizeRead}. */
    private void authorizeRead(ActorContext actor) {
        if (actor.canReadErp()) {
            return;
        }
        AuthorizationDecision d = authorizationPort.evaluate(actor, RequiredScope.READ, null);
        if (d.outcome() == AuthorizationDecision.Outcome.DENY_ROLE) {
            throw new PermissionDeniedException(
                    d.reason() == null ? "required role not present" : d.reason());
        }
        if (d.outcome() == AuthorizationDecision.Outcome.DENY_SCOPE) {
            throw new DataScopeForbiddenException(
                    d.reason() == null ? "target outside data scope" : d.reason());
        }
    }
}

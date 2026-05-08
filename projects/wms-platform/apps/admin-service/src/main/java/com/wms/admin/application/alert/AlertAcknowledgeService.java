package com.wms.admin.application.alert;

import com.wms.admin.domain.error.AlertNotFoundException;
import com.wms.admin.domain.error.StateTransitionInvalidException;
import com.wms.admin.readmodel.alert.AlertLogEntity;
import com.wms.admin.readmodel.alert.AlertLogRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acknowledges an alert. The single application-layer write path on a
 * read-model table — only {@code acknowledged_at} / {@code acknowledged_by}
 * are mutated (architecture.md § Forbidden Patterns + admin-service-api.md
 * § 1.6 Justification).
 *
 * <p>State machine: an alert without {@code acknowledged_at} → set both
 * fields. A second acknowledge attempt → {@code STATE_TRANSITION_INVALID}.
 */
@Service
public class AlertAcknowledgeService {

    private final AlertLogRepository alertRepo;
    private final Clock clock;

    public AlertAcknowledgeService(AlertLogRepository alertRepo, Clock clock) {
        this.alertRepo = alertRepo;
        this.clock = clock;
    }

    @Transactional
    public AlertLogEntity acknowledge(UUID alertId, String actorId) {
        AlertLogEntity row = alertRepo.findById(alertId)
                .orElseThrow(() -> new AlertNotFoundException(alertId));
        if (row.getAcknowledgedAt() != null) {
            throw new StateTransitionInvalidException(
                    "alert " + row.getId() + " already acknowledged");
        }
        row.acknowledge(actorId, clock.instant());
        return row;
    }
}

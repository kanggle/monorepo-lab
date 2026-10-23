package com.example.scmplatform.procurement.infrastructure.persistence.jpa;

import com.example.scmplatform.procurement.domain.po.repository.PurchaseOrderMetricsQuery;
import com.example.scmplatform.procurement.domain.po.repository.ReceivedTransitionRow;
import com.example.scmplatform.procurement.domain.po.repository.SubmittedPoRow;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** JPA adapter for {@link PurchaseOrderMetricsQuery} (TASK-MONO-810). */
@Component
@RequiredArgsConstructor
public class PurchaseOrderMetricsQueryImpl implements PurchaseOrderMetricsQuery {

    private final PurchaseOrderJpaRepository poJpa;
    private final PoStatusHistoryJpaRepository historyJpa;

    @Override
    public List<SubmittedPoRow> submittedIn(String tenantId, Instant from, Instant to) {
        return poJpa.findSubmittedIn(tenantId, from, to);
    }

    @Override
    public List<ReceivedTransitionRow> receivedIn(String tenantId, Instant from, Instant to) {
        return historyJpa.findTransitionsIn(tenantId, PoStatus.RECEIVED, from, to);
    }
}

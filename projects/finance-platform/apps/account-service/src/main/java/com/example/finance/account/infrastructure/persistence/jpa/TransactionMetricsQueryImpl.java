package com.example.finance.account.infrastructure.persistence.jpa;

import com.example.finance.account.domain.transaction.repository.SettledTransactionRow;
import com.example.finance.account.domain.transaction.repository.TransactionMetricsQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** JPA adapter for {@link TransactionMetricsQuery} (TASK-MONO-812). */
@Component
@RequiredArgsConstructor
public class TransactionMetricsQueryImpl implements TransactionMetricsQuery {

    private final TransactionJpaRepository jpa;

    @Override
    public List<SettledTransactionRow> settledIn(String tenantId, Instant from, Instant to) {
        return jpa.findSettledIn(tenantId, from, to);
    }
}

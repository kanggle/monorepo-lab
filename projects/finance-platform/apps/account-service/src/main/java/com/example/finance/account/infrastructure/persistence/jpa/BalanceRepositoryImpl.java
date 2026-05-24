package com.example.finance.account.infrastructure.persistence.jpa;

import com.example.finance.account.domain.balance.Balance;
import com.example.finance.account.domain.balance.Hold;
import com.example.finance.account.domain.balance.HoldStatus;
import com.example.finance.account.domain.balance.repository.BalanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class BalanceRepositoryImpl implements BalanceRepository {

    private final BalanceJpaRepository balanceJpa;
    private final HoldJpaRepository holdJpa;

    @Override
    public Balance save(Balance balance) {
        return balanceJpa.save(balance);
    }

    @Override
    public Optional<Balance> findByAccountId(String accountId, String tenantId) {
        return balanceJpa.findFirstByAccountIdAndTenantId(accountId, tenantId);
    }

    @Override
    public List<Balance> findAllByAccountId(String accountId, String tenantId) {
        return balanceJpa.findAllByAccountIdAndTenantId(accountId, tenantId);
    }

    @Override
    public Hold saveHold(Hold hold) {
        return holdJpa.save(hold);
    }

    @Override
    public Optional<Hold> findHoldById(String holdId, String tenantId) {
        return holdJpa.findByIdAndTenantId(holdId, tenantId);
    }

    @Override
    public List<Hold> findActiveExpiredHolds(Instant beforeEpoch, int limit) {
        return holdJpa.findByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
                HoldStatus.ACTIVE, beforeEpoch, PageRequest.of(0, limit));
    }
}

package com.example.finance.account.infrastructure.persistence.jpa;

import com.example.finance.account.domain.transaction.Transaction;
import com.example.finance.account.domain.transaction.TransactionType;
import com.example.finance.account.domain.transaction.repository.TransactionRepository;
import com.example.finance.account.domain.transaction.status.TransactionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TransactionRepositoryImpl implements TransactionRepository {

    private final TransactionJpaRepository jpa;

    @Override
    public Transaction save(Transaction transaction) {
        return jpa.save(transaction);
    }

    @Override
    public Optional<Transaction> findById(String id, String tenantId) {
        return jpa.findByIdAndTenantId(id, tenantId);
    }

    @Override
    public Page findByAccountId(String accountId,
                                String tenantId,
                                TransactionType type,
                                TransactionStatus status,
                                int page,
                                int size) {
        org.springframework.data.domain.Page<Transaction> p =
                jpa.search(accountId, tenantId, type, status, PageRequest.of(page, size));
        return new Page(p.getContent(), p.getNumber(), p.getSize(),
                p.getTotalElements(), p.getTotalPages());
    }
}

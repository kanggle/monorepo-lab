package com.example.finance.ledger.infrastructure.config;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.domain.account.LedgerAccount;
import com.example.finance.ledger.domain.account.LedgerAccountCodes;
import com.example.finance.ledger.domain.account.LedgerAccountType;
import com.example.finance.ledger.domain.account.repository.LedgerAccountRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;

/**
 * Seeds the two platform GL accounts ({@code CASH_CLEARING},
 * {@code SETTLEMENT_SUSPENSE}) at startup, idempotently (architecture.md
 * § Chart of Accounts). Per-customer wallet accounts
 * ({@code CUSTOMER_WALLET:{accountId}}) are created lazily on first posting by
 * {@code PostJournalEntryUseCase}, NOT seeded here.
 */
@Slf4j
@Configuration
public class ChartOfAccountsSeedConfig {

    @Value("${financeplatform.oauth2.required-tenant-id:finance}")
    private String tenantId;

    @Bean
    public ApplicationRunner chartOfAccountsSeeder(LedgerAccountRepository repository,
                                                   ClockPort clock) {
        return args -> {
            Instant now = clock.now();
            seed(repository, LedgerAccountCodes.CASH_CLEARING, LedgerAccountType.ASSET, now);
            seed(repository, LedgerAccountCodes.SETTLEMENT_SUSPENSE, LedgerAccountType.ASSET, now);
        };
    }

    private void seed(LedgerAccountRepository repository, String code,
                      LedgerAccountType type, Instant now) {
        if (!repository.existsByCode(code, tenantId)) {
            repository.save(LedgerAccount.of(code, tenantId, type, now));
            log.info("Seeded chart-of-accounts node {} ({})", code, type);
        }
    }
}

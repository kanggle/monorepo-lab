package com.example.finance.account;

import com.example.messaging.outbox.OutboxMetricsAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * finance-platform account-service entry point.
 *
 * <p>v1 bootstrap (TASK-MONO-114, ADR-MONO-008 ACCEPTED Option C) — minimal
 * {@code @SpringBootApplication} skeleton with NO business logic. The Account
 * domain (KYC, available/ledger balance, hold/release/capture, account state
 * machine, idempotent fund movement, immutable audit_log) is implemented by
 * TASK-FIN-BE-001 following the to-be-authored Hexagonal service
 * {@code architecture.md}.
 *
 * <p>Planned dependencies: MySQL ({@code finance_db}), Redis (idempotency
 * cache), GAP IdP (OAuth2 Resource Server, RS256 JWKS, {@code tenant_id=finance}
 * fail-closed gate). See {@code projects/finance-platform/PROJECT.md} +
 * {@code specs/integration/iam-integration.md}.
 *
 * <p>{@link OutboxMetricsAutoConfiguration} stays excluded (this service publishes via its
 * own {@code OutboxRow} relay and supplies its own failure handling). The companion
 * {@code exclude = OutboxAutoConfiguration.class} was dropped by TASK-MONO-406, which
 * deleted that auto-config along with the library's fleet-wide {@code ProcessedEvent}
 * entity/repository — per ADR-MONO-004 a dedupe entity belongs to the service, not the
 * shared library. There is nothing left to exclude.
 */
@SpringBootApplication(exclude = OutboxMetricsAutoConfiguration.class)
@EnableScheduling
public class AccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }
}

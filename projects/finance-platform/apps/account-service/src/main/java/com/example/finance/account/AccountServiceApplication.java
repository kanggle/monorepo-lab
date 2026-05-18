package com.example.finance.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

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
 * {@code specs/integration/gap-integration.md}.
 */
@SpringBootApplication
public class AccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }
}

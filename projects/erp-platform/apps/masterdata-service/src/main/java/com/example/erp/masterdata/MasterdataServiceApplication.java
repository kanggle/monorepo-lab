package com.example.erp.masterdata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * erp-platform masterdata-service entry point.
 *
 * <p>v1 bootstrap (TASK-MONO-119, ADR-MONO-016 ACCEPTED Option C) — minimal
 * {@code @SpringBootApplication} skeleton with NO business logic. The
 * organization master-data domain (부서 계층 / 직원 조직속성 / 직급 /
 * 비용센터 / 거래처, reference integrity, effective dating, SSO + permission
 * matrix fail-closed, immutable audit_log) is implemented by TASK-ERP-BE-001
 * following the to-be-authored Hexagonal service {@code architecture.md}.
 *
 * <p>Planned dependencies: MySQL ({@code erp_db}), Redis (idempotency /
 * short-lived cache), GAP IdP (OAuth2 Resource Server, RS256 JWKS,
 * {@code tenant_id=erp} fail-closed gate, internal-only boundary). See
 * {@code projects/erp-platform/PROJECT.md} +
 * {@code specs/integration/iam-integration.md}.
 */
@SpringBootApplication
public class MasterdataServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MasterdataServiceApplication.class, args);
    }
}

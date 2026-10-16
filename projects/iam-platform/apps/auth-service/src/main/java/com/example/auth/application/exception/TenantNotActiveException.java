package com.example.auth.application.exception;

/**
 * TASK-BE-639 (owner decision 2026-10-16 UTC, Q1 = B) — account-service answered the entitled-domains read with a
 * {@code tenantStatus} that is positively not {@code ACTIVE} ({@code PENDING} · {@code SUSPENDED}).
 *
 * <p>Deliberately <b>not</b> an {@link AccountServiceUnavailableException}: «the tenant is not active» is an answer,
 * «could not ask» is not. The operator assume exchange refuses on this one ({@code invalid_grant} +
 * {@code tenant_not_active}) and stays fail-soft on the other. Every other caller of the read treats it as «no
 * entitled domains» — which is what the response's {@code domainKeys: []} says anyway.
 */
public class TenantNotActiveException extends RuntimeException {

    private final String tenantId;
    private final String tenantStatus;

    public TenantNotActiveException(String tenantId, String tenantStatus) {
        super("tenant '" + tenantId + "' is not ACTIVE (status=" + tenantStatus + ")");
        this.tenantId = tenantId;
        this.tenantStatus = tenantStatus;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getTenantStatus() {
        return tenantStatus;
    }
}

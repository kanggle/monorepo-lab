package com.kanggle.platformconsole.bff.application.port.outbound;

import java.util.Map;

/**
 * Narrow outbound port: erp notification-inbox read + mark-read for the
 * notification aggregator (ADR-MONO-043 P3a / D2).
 *
 * <p>Mirrors {@link ErpDepartmentsReadPort} (the Operator Overview erp leg)
 * but targets the {@code /api/erp/notifications} inbox surface defined by
 * {@code platform/contracts/notification-inbox-contract.md} § 2.
 *
 * <p><b>erp divergence (D6 / contract § 3)</b>: erp resolves the recipient
 * from the JWT {@code sub} and the tenant from the JWT {@code tenant_id ∈
 * {erp,*}} claim — it expects <b>NO {@code X-Tenant-Id} header</b>. The
 * credential is the GAP/IAM OIDC access token ({@code IamOidcAccessToken}).
 * That is why this port takes <b>no {@code tenantId} param</b> (unlike the
 * {@link DomainReadPort} family whose every leg forwards {@code X-Tenant-Id});
 * the implementing adapter MUST NOT set {@code X-Tenant-Id}.
 *
 * <p>Read-through, no central store (contract § 4.5): the aggregator reads the
 * domain's authoritative inbox live and proxies mark-read to the owning domain.
 */
public interface ErpNotificationsReadPort {

    /**
     * Reads the caller's erp notification inbox.
     *
     * @param credential the GAP/IAM OIDC access token bearer value (no {@code Bearer } prefix)
     * @param page       page index (≥ 0)
     * @param size       page size (1–100)
     * @param unread     {@code true} = unread only; {@code false} = read only;
     *                   {@code null} = all (contract § 2.1)
     * @return the raw domain response body ({@code { data: [...], meta: {...} }})
     */
    Map<String, Object> readInbox(String credential, int page, int size, Boolean unread);

    /**
     * Idempotently marks a single erp notification as read (contract § 2 row 3).
     *
     * @param credential the GAP/IAM OIDC access token bearer value (no {@code Bearer } prefix)
     * @param id         the notification id owned by the caller
     * @return the raw domain response body ({@code { data: {...}, meta: {...} }})
     */
    Map<String, Object> markRead(String credential, String id);
}

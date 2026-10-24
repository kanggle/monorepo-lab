package com.example.security.service.domain.history;

import java.time.Instant;

public class LoginHistoryEntry {

    private final String tenantId;
    /**
     * TASK-BE-653 — the site a consumer-pool account logged in to (the event's optional
     * {@code siteTenantId}); {@code null} for every other row. {@link #tenantId} is unchanged by it.
     */
    private final String siteTenantId;
    private final String eventId;
    private final String accountId;
    private final LoginOutcome outcome;
    private final String ipMasked;
    private final String userAgentFamily;
    private final String deviceFingerprint;
    private final String geoCountry;
    private final Instant occurredAt;

    public LoginHistoryEntry(String tenantId, String eventId, String accountId, LoginOutcome outcome,
                             String ipMasked, String userAgentFamily,
                             String deviceFingerprint, String geoCountry,
                             Instant occurredAt) {
        this(tenantId, null, eventId, accountId, outcome, ipMasked, userAgentFamily, deviceFingerprint,
                geoCountry, occurredAt);
    }

    /** TASK-BE-653 — with the optional {@code siteTenantId} (blank → {@code null}). */
    public LoginHistoryEntry(String tenantId, String siteTenantId, String eventId, String accountId,
                             LoginOutcome outcome, String ipMasked, String userAgentFamily,
                             String deviceFingerprint, String geoCountry,
                             Instant occurredAt) {
        if (tenantId == null || tenantId.isBlank()) {
            // TASK-BE-248: tenant_id is mandatory. The append-only login_history
            // table has a NOT NULL constraint, so rejecting blanks at the domain
            // boundary keeps the failure mode cheap (no DB round-trip on bad
            // input).
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("outcome must not be null");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt must not be null");
        }
        this.tenantId = tenantId;
        this.siteTenantId = siteTenantId == null || siteTenantId.isBlank() ? null : siteTenantId;
        this.eventId = eventId;
        this.accountId = accountId;
        this.outcome = outcome;
        this.ipMasked = ipMasked;
        this.userAgentFamily = userAgentFamily;
        this.deviceFingerprint = deviceFingerprint;
        this.geoCountry = geoCountry;
        this.occurredAt = occurredAt;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getSiteTenantId() {
        return siteTenantId;
    }

    public String getEventId() {
        return eventId;
    }

    public String getAccountId() {
        return accountId;
    }

    public LoginOutcome getOutcome() {
        return outcome;
    }

    public String getIpMasked() {
        return ipMasked;
    }

    public String getUserAgentFamily() {
        return userAgentFamily;
    }

    public String getDeviceFingerprint() {
        return deviceFingerprint;
    }

    public String getGeoCountry() {
        return geoCountry;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}

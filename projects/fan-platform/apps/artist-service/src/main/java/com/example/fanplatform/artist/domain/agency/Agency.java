package com.example.fanplatform.artist.domain.agency;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Aggregate root for an agency (소속사) — ADR-MONO-079 D1 (an entity inside
 * artist-service, tenant-scoped, {@code UNIQUE(tenant_id, name)}) and D2 (the 0..1
 * store seller that sells its goods, held on the fan side). TASK-MONO-748.
 *
 * <p>Framework-free POJO; JPA mapping lives on the adapter-side entity.
 */
public final class Agency {

    public static final int NAME_MAX = 120;
    public static final int STORE_SELLER_ID_MAX = 64;

    /**
     * ASCII whitespace — exactly the class {@code V4__agencies.sql} collapses
     * ({@code [ \t\n\r\f\v]}). Kept identical on purpose: a name typed through the API
     * must land on the row the free-text move created for the same text.
     */
    private static final Pattern WHITESPACE_RUN = Pattern.compile("[ \\t\\n\\r\\f\\u000B]+");

    private final AgencyId id;
    private final String tenantId;
    private String name;
    private AgencyStatus status;
    private String storeSellerId;
    private final Instant createdAt;
    private Instant updatedAt;
    private final long version;

    private Agency(AgencyId id, String tenantId, String name, AgencyStatus status,
                   String storeSellerId, Instant createdAt, Instant updatedAt, long version) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.status = status;
        this.storeSellerId = storeSellerId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    /**
     * The normalisation rule shared with the V4 data move (written down in that
     * migration's header and in {@code data-model.md} § V4):
     * runs of ASCII whitespace collapse to one space, leading/trailing space is
     * removed, <b>case is preserved</b>. Returns {@code null} when nothing is left —
     * "no agency".
     */
    public static String normalizeName(String raw) {
        if (raw == null) {
            return null;
        }
        String collapsed = WHITESPACE_RUN.matcher(raw).replaceAll(" ");
        int start = 0;
        int end = collapsed.length();
        while (start < end && collapsed.charAt(start) == ' ') start++;
        while (end > start && collapsed.charAt(end - 1) == ' ') end--;
        String out = collapsed.substring(start, end);
        return out.isEmpty() ? null : out;
    }

    public static Agency create(AgencyId id, String tenantId, String rawName) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        String name = validName(rawName);
        Instant now = Instant.now();
        return new Agency(id, tenantId, name, AgencyStatus.ACTIVE, null, now, now, 0L);
    }

    public static Agency reconstitute(AgencyId id, String tenantId, String name, AgencyStatus status,
                                      String storeSellerId, Instant createdAt, Instant updatedAt,
                                      long version) {
        return new Agency(id, tenantId, name, status, storeSellerId, createdAt, updatedAt, version);
    }

    public void rename(String rawName) {
        requireActive("rename");
        this.name = validName(rawName);
        this.updatedAt = Instant.now();
    }

    public void archive() {
        requireActive("archive");
        this.status = AgencyStatus.ARCHIVED;
        this.updatedAt = Instant.now();
    }

    /**
     * Sets (or, with {@code null}, clears) the store seller. The caller — the
     * application service — has ALREADY verified the seller against the store; this
     * method only enforces the local invariants.
     */
    public void linkStoreSeller(String sellerId) {
        requireActive("change the store seller of");
        if (sellerId != null) {
            if (sellerId.isBlank()) {
                throw new IllegalArgumentException("storeSellerId must not be blank");
            }
            if (sellerId.length() > STORE_SELLER_ID_MAX) {
                throw new IllegalArgumentException(
                        "storeSellerId exceeds " + STORE_SELLER_ID_MAX + " chars");
            }
        }
        this.storeSellerId = sellerId;
        this.updatedAt = Instant.now();
    }

    public boolean isActive() {
        return status == AgencyStatus.ACTIVE;
    }

    private void requireActive(String what) {
        if (status == AgencyStatus.ARCHIVED) {
            throw new IllegalStateException("cannot " + what + " an ARCHIVED agency");
        }
    }

    private static String validName(String rawName) {
        String name = normalizeName(rawName);
        if (name == null) {
            throw new IllegalArgumentException("agency name must not be blank");
        }
        if (name.length() > NAME_MAX) {
            throw new IllegalArgumentException("agency name exceeds " + NAME_MAX + " chars");
        }
        return name;
    }

    public AgencyId getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getName() { return name; }
    public AgencyStatus getStatus() { return status; }
    public String getStoreSellerId() { return storeSellerId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}

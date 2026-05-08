package com.wms.admin.domain;

import com.wms.admin.domain.error.SettingImmutableFieldException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Runtime configuration aggregate. {@code key} + {@code scope} +
 * {@code warehouseId} are immutable after seed; only {@code valueJson}
 * (and bookkeeping) may change via PUT.
 *
 * <p>{@code valueJson} must satisfy {@code schemaJson} (JSON Schema
 * draft-07) at write time — validation lives in the application service.
 */
public final class Setting {

    private final String key;
    private final SettingScope scope;
    private final UUID warehouseId;
    private final String valueJson;
    private final String schemaJson;
    private final String description;
    private final long version;
    private final Instant createdAt;
    private final String createdBy;
    private final Instant updatedAt;
    private final String updatedBy;

    public Setting(String key, SettingScope scope, UUID warehouseId,
                   String valueJson, String schemaJson, String description,
                   long version, Instant createdAt, String createdBy,
                   Instant updatedAt, String updatedBy) {
        this.key = Objects.requireNonNull(key, "key");
        this.scope = Objects.requireNonNull(scope, "scope");
        if (scope == SettingScope.WAREHOUSE && warehouseId == null) {
            throw new SettingImmutableFieldException("warehouseId required for WAREHOUSE scope");
        }
        if (scope == SettingScope.GLOBAL && warehouseId != null) {
            throw new SettingImmutableFieldException("warehouseId must be null for GLOBAL scope");
        }
        this.warehouseId = warehouseId;
        this.valueJson = Objects.requireNonNull(valueJson, "valueJson");
        this.schemaJson = Objects.requireNonNull(schemaJson, "schemaJson");
        this.description = description;
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.createdBy = createdBy;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.updatedBy = updatedBy;
    }

    /**
     * Update {@code valueJson} only. {@code key} / {@code scope} /
     * {@code warehouseId} / {@code schemaJson} are immutable in v1.
     */
    public Setting withValue(String newValueJson, Instant now, String actor) {
        return new Setting(key, scope, warehouseId, newValueJson, schemaJson, description,
                version, createdAt, createdBy, now, actor);
    }

    public String key() { return key; }
    public SettingScope scope() { return scope; }
    public UUID warehouseId() { return warehouseId; }
    public String valueJson() { return valueJson; }
    public String schemaJson() { return schemaJson; }
    public String description() { return description; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }
    public String createdBy() { return createdBy; }
    public Instant updatedAt() { return updatedAt; }
    public String updatedBy() { return updatedBy; }
}

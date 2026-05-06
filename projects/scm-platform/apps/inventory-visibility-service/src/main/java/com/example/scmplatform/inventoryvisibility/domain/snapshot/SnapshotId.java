package com.example.scmplatform.inventoryvisibility.domain.snapshot;

import java.util.Objects;
import java.util.UUID;

public record SnapshotId(UUID value) {

    public SnapshotId {
        Objects.requireNonNull(value, "SnapshotId value must not be null");
    }

    public static SnapshotId of(UUID value) {
        return new SnapshotId(value);
    }

    public static SnapshotId of(String value) {
        return new SnapshotId(UUID.fromString(value));
    }

    public static SnapshotId generate() {
        return new SnapshotId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

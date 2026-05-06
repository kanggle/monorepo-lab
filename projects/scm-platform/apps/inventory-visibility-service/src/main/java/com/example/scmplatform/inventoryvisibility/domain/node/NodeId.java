package com.example.scmplatform.inventoryvisibility.domain.node;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly-typed value object wrapping a node UUID.
 */
public record NodeId(UUID value) {

    public NodeId {
        Objects.requireNonNull(value, "NodeId value must not be null");
    }

    public static NodeId of(UUID value) {
        return new NodeId(value);
    }

    public static NodeId of(String value) {
        return new NodeId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

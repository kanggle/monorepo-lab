package com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.adapter;

import com.example.scmplatform.inventoryvisibility.domain.error.ReadModelCorruptException;

import java.util.UUID;

/**
 * Parses UUID-typed columns when reconstructing a persisted read-model row into
 * its domain object.
 *
 * <p>A malformed persisted value is a server-side data-integrity fault, NOT a
 * client input error. Routing it through {@link ReadModelCorruptException}
 * (mapped to 500 + logged) instead of letting a bare {@link
 * IllegalArgumentException} escape avoids the misleading {@code 422} with no log
 * trail that masked TASK-MONO-171. Controller-boundary parsing of client input
 * (e.g. a bad {@code nodeId} path variable) is deliberately left as a plain
 * {@link IllegalArgumentException} → {@code 422}: there the client genuinely sent
 * a bad value.
 */
final class ReadModelIds {

    private ReadModelIds() {
    }

    /**
     * @param value  the persisted string value
     * @param column {@code table.column} for the diagnostic message
     * @return the parsed UUID
     * @throws ReadModelCorruptException if {@code value} is null or not a UUID
     */
    static UUID requireUuid(String value, String column) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ReadModelCorruptException(column, value, e);
        }
    }
}

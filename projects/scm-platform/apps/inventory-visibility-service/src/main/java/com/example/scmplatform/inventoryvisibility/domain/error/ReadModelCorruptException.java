package com.example.scmplatform.inventoryvisibility.domain.error;

/**
 * A persisted read-model row holds a value that cannot be reconstructed into its
 * domain type (e.g. a non-UUID id column). This is a server-side data-integrity
 * fault — the client did nothing wrong — so it is mapped to {@code 500
 * INTERNAL_ERROR} and logged, distinct from client input validation ({@code 422
 * VALIDATION_ERROR}).
 *
 * <p>Background (TASK-SCM-BE-021 / TASK-MONO-171): a non-UUID {@code
 * inventory_nodes.id} seeded into the read-model surfaced as a bare {@link
 * IllegalArgumentException} from {@code UUID.fromString(...)} during {@code
 * toDomain} reconstruction. The web advice mapped that to a misleading {@code
 * 422} with no log line, so the operator console showed "degraded" with zero
 * diagnostic trail. This exception restores the correct status + logging.
 */
public class ReadModelCorruptException extends RuntimeException {

    public ReadModelCorruptException(String column, String value, Throwable cause) {
        super("Corrupt read-model value in column '" + column + "': " + value, cause);
    }
}

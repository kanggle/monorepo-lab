package com.example.scmplatform.inventoryvisibility.adapter.inbound.messaging;

import java.util.Map;

/**
 * Utility class for extracting typed fields from a WMS event envelope payload map.
 *
 * <p>Extracted from the three Kafka consumer classes
 * ({@code WmsInventoryReceivedConsumer}, {@code WmsInventoryAdjustedConsumer},
 * {@code WmsInventoryTransferredConsumer}) where identical {@code getStringField} /
 * {@code getLongField} private methods were duplicated (TASK-SCM-BE-016 L6).
 *
 * <p>All methods are {@code static} — this class has no state and requires no
 * Spring bean registration.
 */
final class WmsEnvelopeParser {

    private WmsEnvelopeParser() {
    }

    /**
     * Extracts a non-null string value from {@code map} at {@code key}.
     *
     * @throws InvalidEnvelopeException if the key is absent or the value is null
     */
    static String getStringField(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) {
            throw new InvalidEnvelopeException("Missing field: " + key);
        }
        return val.toString();
    }

    /**
     * Extracts a long value from {@code map} at {@code key}.
     * Returns {@code 0} if the key is absent or the value is null.
     */
    static long getLongField(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) {
            return 0L;
        }
        if (val instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(val.toString());
    }

    /**
     * Signals an envelope validation failure — the consumer should route the
     * message to the DLT without retry.
     */
    static class InvalidEnvelopeException extends RuntimeException {
        InvalidEnvelopeException(String msg) {
            super(msg);
        }
    }
}

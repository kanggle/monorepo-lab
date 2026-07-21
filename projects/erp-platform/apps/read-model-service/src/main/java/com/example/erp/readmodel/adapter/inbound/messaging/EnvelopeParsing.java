package com.example.erp.readmodel.adapter.inbound.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.function.Predicate;

/**
 * Shared envelope parse-and-validate for the read-model command mappers
 * (TASK-ERP-BE-034). The three {@code *EnvelopeToCommandMapper}s repeated the
 * same {@code readValue} → {@link InvalidEnvelopeException} and
 * null-or-invalid → {@link InvalidEnvelopeException} try/catch, differing only in
 * the envelope type, the {@code "<kind> envelope"} label, and the
 * {@code "(missing …)"} field list. Extracted here with the exact per-mapper
 * messages preserved (parameterized on {@code label}/{@code missingFields}), so
 * an unparseable or invalid envelope is still rejected identically and routed to
 * the DLT without retry (Failure Mode 2).
 */
final class EnvelopeParsing {

    private EnvelopeParsing() {
    }

    /**
     * Deserializes {@code rawValue} to {@code type} and asserts {@code valid};
     * throws {@link InvalidEnvelopeException} with the original per-mapper message
     * on unparseable input or a {@code null}/invalid envelope.
     *
     * @param label         envelope-kind label spliced into the message —
     *                      {@code ""}, {@code "approval "}, or {@code "delegation "}
     *                      (note the trailing space when non-empty)
     * @param missingFields the {@code "(missing …)"} field list for the invalid case
     * @param valid         the envelope's own validity predicate (e.g. {@code isValid})
     */
    static <T> T parseAndValidate(ObjectMapper objectMapper, String rawValue, String topic,
                                  Class<T> type, String label, String missingFields,
                                  Predicate<T> valid) {
        T envelope;
        try {
            envelope = objectMapper.readValue(rawValue, type);
        } catch (Exception e) {
            throw new InvalidEnvelopeException("Unparseable " + label + "envelope on topic " + topic
                    + ": " + e.getMessage());
        }
        if (envelope == null || !valid.test(envelope)) {
            throw new InvalidEnvelopeException("Invalid " + label + "envelope (missing "
                    + missingFields + ") on topic " + topic);
        }
        return envelope;
    }
}

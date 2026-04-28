package com.wms.inbound.adapter.in.webhook.erp;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verifies the {@code X-Erp-Timestamp} header against the configured
 * acceptance window.
 *
 * <p>Per {@code specs/contracts/webhooks/erp-asn-webhook.md} § Timestamp
 * Verification:
 * <pre>
 *   abs(serverNow - X-Erp-Timestamp) ≤ 5 minutes
 * </pre>
 *
 * <p>Failure modes (each returns {@code false}):
 * <ul>
 *   <li>Header absent or blank</li>
 *   <li>Unparseable timestamp (anything not ISO-8601)</li>
 *   <li>Outside the configured window (default ±300 s)</li>
 * </ul>
 *
 * <p>Window is configurable via {@code inbound.webhook.erp.timestamp-window-seconds};
 * production must NOT widen beyond 600 s per spec.
 */
@Component
public class TimestampWindowVerifier {

    private final Clock clock;
    private final Duration window;

    public TimestampWindowVerifier(
            Clock clock,
            @Value("${inbound.webhook.erp.timestamp-window-seconds:300}") long windowSeconds) {
        this.clock = clock;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    /**
     * @return {@code true} if {@code timestampHeader} is parseable and within
     *         the acceptance window of the configured clock
     */
    public boolean isWithinWindow(String timestampHeader) {
        if (timestampHeader == null || timestampHeader.isBlank()) {
            return false;
        }
        Instant signed;
        try {
            signed = Instant.parse(timestampHeader);
        } catch (DateTimeParseException e) {
            return false;
        }
        Instant now = clock.instant();
        Duration drift = Duration.between(signed, now).abs();
        return drift.compareTo(window) <= 0;
    }

    public Duration getWindow() {
        return window;
    }
}

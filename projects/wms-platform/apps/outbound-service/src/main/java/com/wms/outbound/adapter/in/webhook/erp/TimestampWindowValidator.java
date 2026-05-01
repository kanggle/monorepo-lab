package com.wms.outbound.adapter.in.webhook.erp;

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
 * <p>Per {@code specs/contracts/webhooks/erp-order-webhook.md} § Timestamp
 * Verification:
 * <pre>
 *   abs(serverNow - X-Erp-Timestamp) ≤ 5 minutes
 * </pre>
 *
 * <p>Window is configurable via
 * {@code outbound.webhook.erp.timestamp-window-seconds}; default 300 s.
 */
@Component
public class TimestampWindowValidator {

    private final Clock clock;
    private final Duration window;

    public TimestampWindowValidator(
            Clock clock,
            @Value("${outbound.webhook.erp.timestamp-window-seconds:300}") long windowSeconds) {
        this.clock = clock;
        this.window = Duration.ofSeconds(windowSeconds);
    }

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

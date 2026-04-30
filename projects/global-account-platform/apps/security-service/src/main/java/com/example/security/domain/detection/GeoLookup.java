package com.example.security.domain.detection;

import java.util.Optional;

/**
 * Port for IP → geo lookup. Typically backed by MaxMind GeoLite2.
 *
 * <p>Implementations must return {@link Optional#empty()} when the lookup cannot be
 * performed (DB file missing, IP not found, IP masked beyond usability). Rules that
 * depend on this port must gracefully skip when empty.</p>
 */
public interface GeoLookup {

    /** Whether the backing GeoIP DB is loaded and usable. */
    boolean isAvailable();

    /**
     * Resolve the IP string to a geo point. Accepts the raw client IP; masked IPs
     * (e.g. {@code "192.168.1.***"}) will typically yield empty.
     */
    Optional<GeoPoint> resolve(String ip);
}

package com.example.security.domain.detection;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * GeoAnomalyRule — fires when the distance between the previous successful
 * login and the current successful login requires physically-impossible travel
 * (faster than {@link DetectionThresholds#geoSpeedKmPerHour()}, default 900 km/h).
 *
 * <p>Disabled gracefully when:
 * <ul>
 *   <li>the GeoIP DB is not available ({@link GeoLookup#isAvailable()} == false)</li>
 *   <li>the raw IP cannot be resolved to a geo point (masked IP, unknown range)</li>
 *   <li>no previous geo snapshot exists for the account</li>
 * </ul>
 * </p>
 *
 * <p>On a fire, the rule writes the <em>new</em> snapshot back so that
 * subsequent events compare to the most recent success. If the rule does not
 * fire (same country / missing data), the snapshot is still refreshed so that
 * the baseline tracks the user. Evidence contains country, distance, and time
 * delta (no raw IP).</p>
 */
public class GeoAnomalyRule implements SuspiciousActivityRule {

    public static final String CODE = "GEO_ANOMALY";

    private final GeoLookup geoLookup;
    private final LastKnownGeoStore geoStore;
    private final DetectionThresholds thresholds;

    public GeoAnomalyRule(GeoLookup geoLookup,
                          LastKnownGeoStore geoStore,
                          DetectionThresholds thresholds) {
        this.geoLookup = geoLookup;
        this.geoStore = geoStore;
        this.thresholds = thresholds;
    }

    @Override
    public String ruleCode() {
        return CODE;
    }

    @Override
    public DetectionResult evaluate(EvaluationContext ctx) {
        if (ctx == null || !ctx.isLoginSucceeded() || !ctx.hasAccount()) {
            return DetectionResult.NONE;
        }
        if (!geoLookup.isAvailable()) {
            return DetectionResult.NONE;
        }
        Optional<GeoPoint> maybeCurrent = geoLookup.resolve(ctx.ipMasked());
        if (maybeCurrent.isEmpty()) {
            return DetectionResult.NONE;
        }
        GeoPoint current = maybeCurrent.get();

        // TASK-BE-248 Phase 1: include tenantId in GeoStore key so snapshots are
        // isolated per tenant. Full per-tenant geo anomaly scoring is Phase 2.
        String tenantId = ctx.tenantId() != null ? ctx.tenantId() : "";
        Optional<LastKnownGeoStore.Snapshot> prev = geoStore.get(tenantId, ctx.accountId());

        // Always refresh the snapshot on a successful login so the baseline tracks the user.
        geoStore.put(tenantId, ctx.accountId(),
                new LastKnownGeoStore.Snapshot(
                        current.country(), current.latitude(), current.longitude(), ctx.occurredAt()));

        if (prev.isEmpty()) {
            return DetectionResult.NONE;
        }

        LastKnownGeoStore.Snapshot p = prev.get();
        GeoPoint previous = new GeoPoint(p.country(), p.latitude(), p.longitude());
        double distanceKm = previous.distanceKm(current);
        Duration delta = Duration.between(p.occurredAt(), ctx.occurredAt());
        long deltaSeconds = Math.max(1L, delta.getSeconds());
        double hours = deltaSeconds / 3600.0;
        double speedKmH = distanceKm / Math.max(hours, 1.0 / 3600.0); // avoid div-by-zero

        if (speedKmH <= thresholds.geoSpeedKmPerHour()) {
            return DetectionResult.NONE;
        }

        // Score scales with how far the implied speed exceeds the physical limit.
        double ratio = speedKmH / thresholds.geoSpeedKmPerHour();
        int score = (int) Math.min(100, Math.max(thresholds.geoMinScore(),
                Math.round(thresholds.geoMinScore() + (ratio - 1.0) * thresholds.geoScoreSlope())));

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("description", "Geographically impossible travel between successive logins");
        evidence.put("previousCountry", p.country());
        evidence.put("currentCountry", current.country());
        evidence.put("distanceKm", Math.round(distanceKm));
        evidence.put("timeDeltaSeconds", deltaSeconds);
        evidence.put("impliedSpeedKmH", Math.round(speedKmH));
        evidence.put("speedThresholdKmH", thresholds.geoSpeedKmPerHour());
        // TASK-BE-248 Phase 2a: include tenantId in evidence for cross-tenant audit trail.
        evidence.put("tenantId", tenantId);
        return new DetectionResult(CODE, score, evidence);
    }
}

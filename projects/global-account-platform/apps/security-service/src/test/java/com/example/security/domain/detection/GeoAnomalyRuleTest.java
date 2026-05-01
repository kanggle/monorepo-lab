package com.example.security.domain.detection;

import com.example.security.domain.Tenants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * TASK-BE-248 Phase 1: GeoAnomalyRule now routes LastKnownGeoStore calls through
 * (tenantId, accountId) so that geo baselines are isolated per tenant.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class GeoAnomalyRuleTest {

    private static final String TENANT = Tenants.DEFAULT_TENANT_ID;

    @Mock GeoLookup geoLookup;
    @Mock LastKnownGeoStore geoStore;

    private final DetectionThresholds thresholds = DetectionThresholds.defaults();

    // Seoul (37.5, 127.0) and New York (40.7, -74.0) — ~11000km apart.
    private static final GeoPoint SEOUL = new GeoPoint("KR", 37.5, 127.0);
    private static final GeoPoint NYC = new GeoPoint("US", 40.7, -74.0);

    private EvaluationContext succeededCtx(Instant at) {
        // Use tenant-aware constructor (tenantId required after TASK-BE-248 Phase 1).
        return new EvaluationContext(
                TENANT, "evt-1", "auth.login.succeeded", "acc-1",
                "1.2.3.4", "fp-1", "US", at, null);
    }

    @Test
    @DisplayName("Disabled when GeoIP DB is not available")
    void disabledWhenGeoIpUnavailable() {
        when(geoLookup.isAvailable()).thenReturn(false);
        DetectionResult r = new GeoAnomalyRule(geoLookup, geoStore, thresholds).evaluate(succeededCtx(Instant.now()));
        assertThat(r.fired()).isFalse();
        verifyNoInteractions(geoStore);
    }

    @Test
    @DisplayName("No previous snapshot → does not fire but writes baseline")
    void firstLoginBaselinesOnly() {
        when(geoLookup.isAvailable()).thenReturn(true);
        when(geoLookup.resolve(anyString())).thenReturn(Optional.of(NYC));
        when(geoStore.get(eq(TENANT), eq("acc-1"))).thenReturn(Optional.empty());

        DetectionResult r = new GeoAnomalyRule(geoLookup, geoStore, thresholds).evaluate(succeededCtx(Instant.now()));
        assertThat(r.fired()).isFalse();
        verify(geoStore).put(eq(TENANT), eq("acc-1"), any());
    }

    @Test
    @DisplayName("KR → US in 30 min implies > 900 km/h → AUTO_LOCK (UC-8)")
    void uc8PhysicallyImpossible() {
        Instant prevAt = Instant.parse("2026-04-12T08:00:00Z");
        Instant nowAt = Instant.parse("2026-04-12T08:30:00Z"); // 30 minutes later

        when(geoLookup.isAvailable()).thenReturn(true);
        when(geoLookup.resolve(anyString())).thenReturn(Optional.of(NYC));
        when(geoStore.get(eq(TENANT), eq("acc-1"))).thenReturn(Optional.of(
                new LastKnownGeoStore.Snapshot(SEOUL.country(), SEOUL.latitude(), SEOUL.longitude(), prevAt)));

        DetectionResult r = new GeoAnomalyRule(geoLookup, geoStore, thresholds).evaluate(succeededCtx(nowAt));
        assertThat(r.fired()).isTrue();
        assertThat(r.riskScore()).isGreaterThanOrEqualTo(85);
        assertThat(RiskLevel.fromScore(r.riskScore())).isEqualTo(RiskLevel.AUTO_LOCK);
        assertThat(r.evidence()).containsKeys("previousCountry", "currentCountry", "distanceKm", "impliedSpeedKmH");
    }

    @Test
    @DisplayName("Same-country login within plausible speed → no fire")
    void samePlausibleSpeed() {
        Instant prevAt = Instant.parse("2026-04-12T06:00:00Z");
        Instant nowAt = prevAt.plus(Duration.ofHours(3));

        GeoPoint busan = new GeoPoint("KR", 35.1, 129.0);
        when(geoLookup.isAvailable()).thenReturn(true);
        when(geoLookup.resolve(anyString())).thenReturn(Optional.of(busan));
        when(geoStore.get(eq(TENANT), eq("acc-1"))).thenReturn(Optional.of(
                new LastKnownGeoStore.Snapshot("KR", SEOUL.latitude(), SEOUL.longitude(), prevAt)));

        DetectionResult r = new GeoAnomalyRule(geoLookup, geoStore, thresholds).evaluate(succeededCtx(nowAt));
        assertThat(r.fired()).isFalse();
    }

    @Test
    @DisplayName("Unknown IP (masked) → resolver returns empty → skip")
    void unknownIpSkips() {
        when(geoLookup.isAvailable()).thenReturn(true);
        when(geoLookup.resolve(anyString())).thenReturn(Optional.empty());
        DetectionResult r = new GeoAnomalyRule(geoLookup, geoStore, thresholds).evaluate(succeededCtx(Instant.now()));
        assertThat(r.fired()).isFalse();
        verifyNoInteractions(geoStore);
    }

    @Test
    @DisplayName("Evidence contains tenantId on fire")
    void evidenceContainsTenantId() {
        Instant prevAt = Instant.parse("2026-04-12T08:00:00Z");
        Instant nowAt  = Instant.parse("2026-04-12T08:30:00Z");

        when(geoLookup.isAvailable()).thenReturn(true);
        when(geoLookup.resolve(anyString())).thenReturn(Optional.of(NYC));
        when(geoStore.get(eq(TENANT), eq("acc-1"))).thenReturn(Optional.of(
                new LastKnownGeoStore.Snapshot(SEOUL.country(), SEOUL.latitude(), SEOUL.longitude(), prevAt)));

        DetectionResult r = new GeoAnomalyRule(geoLookup, geoStore, thresholds).evaluate(succeededCtx(nowAt));
        assertThat(r.fired()).isTrue();
        assertThat(r.evidence()).containsKey("tenantId");
        assertThat(r.evidence().get("tenantId")).isEqualTo(TENANT);
    }

    // ── TASK-BE-248 Phase 2a: Cross-Tenant Isolation ───────────────────────────

    @Test
    @DisplayName("[cross-tenant] 테넌트A geo 기준이 테넌트B의 동일 account 탐지에 영향 없음")
    void crossTenantIsolation_tenantABaselineDoesNotAffectTenantB() {
        String tenantA = "tenant-a";
        String tenantB = "tenant-b";
        Instant prevAt = Instant.parse("2026-04-12T08:00:00Z");
        Instant nowAt  = Instant.parse("2026-04-12T08:30:00Z");

        GeoAnomalyRule rule = new GeoAnomalyRule(geoLookup, geoStore, thresholds);

        // tenantA: Seoul → NYC in 30 min → fires
        EvaluationContext ctxA = new EvaluationContext(
                tenantA, "evt-a", "auth.login.succeeded", "acc-1",
                "1.2.3.4", "fp-1", "US", nowAt, null);
        when(geoLookup.isAvailable()).thenReturn(true);
        when(geoLookup.resolve(anyString())).thenReturn(Optional.of(NYC));
        when(geoStore.get(eq(tenantA), eq("acc-1"))).thenReturn(Optional.of(
                new LastKnownGeoStore.Snapshot(SEOUL.country(), SEOUL.latitude(), SEOUL.longitude(), prevAt)));
        DetectionResult resultA = rule.evaluate(ctxA);
        assertThat(resultA.fired()).isTrue();

        // tenantB: no previous snapshot → does not fire
        EvaluationContext ctxB = new EvaluationContext(
                tenantB, "evt-b", "auth.login.succeeded", "acc-1",
                "1.2.3.4", "fp-1", "US", nowAt, null);
        when(geoStore.get(eq(tenantB), eq("acc-1"))).thenReturn(Optional.empty());
        DetectionResult resultB = rule.evaluate(ctxB);
        assertThat(resultB.fired()).isFalse();

        // Each tenant's store must be called with its own key exactly once.
        // tenantA get was called during tenantA evaluation (1 time).
        // tenantB get was called during tenantB evaluation (1 time).
        // Neither tenant's evaluation should bleed into the other's store key.
        verify(geoStore, org.mockito.Mockito.times(1)).get(eq(tenantA), eq("acc-1"));
        verify(geoStore, org.mockito.Mockito.times(1)).get(eq(tenantB), eq("acc-1"));
        // put must be called per tenant independently
        verify(geoStore, org.mockito.Mockito.times(1)).put(eq(tenantA), eq("acc-1"), any());
        verify(geoStore, org.mockito.Mockito.times(1)).put(eq(tenantB), eq("acc-1"), any());
    }
}

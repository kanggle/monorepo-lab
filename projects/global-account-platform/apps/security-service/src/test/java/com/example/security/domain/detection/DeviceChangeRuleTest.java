package com.example.security.domain.detection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceChangeRuleTest {

    @Mock KnownDeviceStore store;

    private final DetectionThresholds thresholds = DetectionThresholds.defaults();

    private EvaluationContext succeededCtx(String fp) {
        return new EvaluationContext("evt-1", "auth.login.succeeded", "acc-1",
                "1.2.3.***", fp, "KR", Instant.now(), null);
    }

    private EvaluationContext succeededCtxWithDevice(String fp, String deviceId, Boolean isNewDevice) {
        return new EvaluationContext("evt-1", "auth.login.succeeded", "acc-1",
                "1.2.3.***", fp, "KR", Instant.now(), null, deviceId, isNewDevice);
    }

    @Test
    @DisplayName("Unknown device fires with configured score (50 by default)")
    void unknownDeviceFires() {
        when(store.isKnown("acc-1", "fp-new")).thenReturn(false);
        DetectionResult r = new DeviceChangeRule(store, thresholds).evaluate(succeededCtx("fp-new"));
        assertThat(r.fired()).isTrue();
        assertThat(r.riskScore()).isEqualTo(50);
        assertThat(RiskLevel.fromScore(r.riskScore())).isEqualTo(RiskLevel.ALERT);
        verify(store).remember("acc-1", "fp-new");
    }

    @Test
    @DisplayName("Known device does not fire but is still remembered (TTL refresh)")
    void knownDeviceSkipped() {
        when(store.isKnown("acc-1", "fp-known")).thenReturn(true);
        DetectionResult r = new DeviceChangeRule(store, thresholds).evaluate(succeededCtx("fp-known"));
        assertThat(r.fired()).isFalse();
        verify(store).remember("acc-1", "fp-known");
    }

    @Test
    @DisplayName("Missing fingerprint → skip")
    void missingFingerprint() {
        DetectionResult r = new DeviceChangeRule(store, thresholds).evaluate(succeededCtx(null));
        assertThat(r.fired()).isFalse();
        verifyNoInteractions(store);
    }

    @Test
    @DisplayName("Configurable score affects outcome (raise to 80 → AUTO_LOCK)")
    void configurableScore() {
        DetectionThresholds strict = new DetectionThresholds(10, 3600, 80, 900, 85, 15, 80, true);
        when(store.isKnown("acc-1", "fp-new")).thenReturn(false);
        DetectionResult r = new DeviceChangeRule(store, strict).evaluate(succeededCtx("fp-new"));
        assertThat(r.riskScore()).isEqualTo(80);
        assertThat(RiskLevel.fromScore(r.riskScore())).isEqualTo(RiskLevel.AUTO_LOCK);
    }

    // --- TASK-BE-025: device_id-based primary path ---

    @Test
    @DisplayName("isNewDevice=true → ALERT regardless of fingerprint match")
    void isNewDeviceTrueFires() {
        DetectionResult r = new DeviceChangeRule(store, thresholds)
                .evaluate(succeededCtxWithDevice("fp-any", "dev-123", true));
        assertThat(r.fired()).isTrue();
        assertThat(r.riskScore()).isEqualTo(50);
        assertThat(RiskLevel.fromScore(r.riskScore())).isEqualTo(RiskLevel.ALERT);
        assertThat(r.evidence()).containsEntry("deviceId", "dev-123");
        // Known-device store must NOT be consulted on primary path.
        verifyNoInteractions(store);
    }

    @Test
    @DisplayName("isNewDevice=false → no alert even if fingerprint is unknown")
    void isNewDeviceFalseSkipsEvenForNewFingerprint() {
        DetectionResult r = new DeviceChangeRule(store, thresholds)
                .evaluate(succeededCtxWithDevice("fp-never-seen", "dev-456", false));
        assertThat(r.fired()).isFalse();
        // fingerprint store is bypassed when the authoritative signal is present.
        verifyNoInteractions(store);
    }

    @Test
    @DisplayName("Absent isNewDevice (legacy event) → fingerprint fallback evaluation preserved")
    void legacyFallbackToFingerprint() {
        // legacy: both deviceId and isNewDevice null → must use KnownDeviceStore path
        when(store.isKnown("acc-1", "fp-new")).thenReturn(false);
        DetectionResult r = new DeviceChangeRule(store, thresholds)
                .evaluate(succeededCtxWithDevice("fp-new", null, null));
        assertThat(r.fired()).isTrue();
        assertThat(r.riskScore()).isEqualTo(50);
        verify(store).remember("acc-1", "fp-new");
    }
}

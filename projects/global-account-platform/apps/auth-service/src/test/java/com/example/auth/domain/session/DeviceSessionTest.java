package com.example.auth.domain.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeviceSessionTest {

    @Test
    @DisplayName("create normalises blank/null fingerprint to UNKNOWN sentinel")
    void createNormalisesUnknownFingerprint() {
        Instant now = Instant.now();
        DeviceSession s1 = DeviceSession.create("d1", "acc", null, "UA", "1.2.3.4", "KR", now);
        DeviceSession s2 = DeviceSession.create("d2", "acc", "  ", "UA", "1.2.3.4", "KR", now);
        DeviceSession s3 = DeviceSession.create("d3", "acc", "fp-xyz", "UA", "1.2.3.4", "KR", now);

        assertThat(s1.getDeviceFingerprint()).isEqualTo(DeviceSession.UNKNOWN_FINGERPRINT);
        assertThat(s2.getDeviceFingerprint()).isEqualTo(DeviceSession.UNKNOWN_FINGERPRINT);
        assertThat(s3.getDeviceFingerprint()).isEqualTo("fp-xyz");
    }

    @Test
    @DisplayName("touch updates last_seen_at, ip and geo on active sessions")
    void touchUpdatesLastSeen() {
        Instant t0 = Instant.parse("2026-04-01T00:00:00Z");
        DeviceSession s = DeviceSession.create("d1", "acc", "fp", "UA", "1.1.1.1", "KR", t0);
        Instant t1 = Instant.parse("2026-04-02T00:00:00Z");

        s.touch(t1, "2.2.2.2", "JP");

        assertThat(s.getLastSeenAt()).isEqualTo(t1);
        assertThat(s.getIpLast()).isEqualTo("2.2.2.2");
        assertThat(s.getGeoLast()).isEqualTo("JP");
        assertThat(s.getIssuedAt()).isEqualTo(t0); // immutable
    }

    @Test
    @DisplayName("touch is a no-op once revoked")
    void touchNoopAfterRevoke() {
        Instant t0 = Instant.parse("2026-04-01T00:00:00Z");
        DeviceSession s = DeviceSession.create("d1", "acc", "fp", "UA", "1.1.1.1", "KR", t0);
        Instant t1 = Instant.parse("2026-04-02T00:00:00Z");
        s.revoke(t1, RevokeReason.USER_REQUESTED);

        s.touch(Instant.parse("2026-04-03T00:00:00Z"), "9.9.9.9", "US");

        assertThat(s.getLastSeenAt()).isEqualTo(t0);
        assertThat(s.getIpLast()).isEqualTo("1.1.1.1");
    }

    @Test
    @DisplayName("revoke is idempotent — first reason wins")
    void revokeIdempotent() {
        Instant t0 = Instant.parse("2026-04-01T00:00:00Z");
        DeviceSession s = DeviceSession.create("d1", "acc", "fp", "UA", "1.1.1.1", "KR", t0);
        s.revoke(Instant.parse("2026-04-02T00:00:00Z"), RevokeReason.EVICTED_BY_LIMIT);
        s.revoke(Instant.parse("2026-04-03T00:00:00Z"), RevokeReason.USER_REQUESTED);

        assertThat(s.getRevokeReason()).isEqualTo(RevokeReason.EVICTED_BY_LIMIT);
        assertThat(s.getRevokedAt()).isEqualTo(Instant.parse("2026-04-02T00:00:00Z"));
        assertThat(s.isRevoked()).isTrue();
        assertThat(s.isActive()).isFalse();
    }

    @Test
    @DisplayName("constructor rejects null deviceId / accountId / fingerprint")
    void constructorRejectsNulls() {
        Instant now = Instant.now();
        assertThatThrownBy(() ->
                new DeviceSession(null, null, "acc", "fp", null, null, null, now, now, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() ->
                new DeviceSession(null, "d", null, "fp", null, null, null, now, now, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() ->
                new DeviceSession(null, "d", "acc", null, null, null, null, now, now, null, null))
                .isInstanceOf(NullPointerException.class);
    }
}

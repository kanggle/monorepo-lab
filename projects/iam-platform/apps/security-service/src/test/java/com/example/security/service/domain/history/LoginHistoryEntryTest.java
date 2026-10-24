package com.example.security.service.domain.history;

import com.example.security.service.domain.Tenants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginHistoryEntryTest {

    @Test
    @DisplayName("Creates valid LoginHistoryEntry")
    void createsValidEntry() {
        LoginHistoryEntry entry = new LoginHistoryEntry(
                Tenants.DEFAULT_TENANT_ID,
                "evt-001", "acc-123", LoginOutcome.SUCCESS,
                "192.168.1.***", "Chrome 120", "abcdef123456",
                "KR", Instant.parse("2026-04-12T10:00:00Z")
        );

        assertThat(entry.getTenantId()).isEqualTo(Tenants.DEFAULT_TENANT_ID);
        assertThat(entry.getEventId()).isEqualTo("evt-001");
        assertThat(entry.getAccountId()).isEqualTo("acc-123");
        assertThat(entry.getOutcome()).isEqualTo(LoginOutcome.SUCCESS);
    }

    @Test
    @DisplayName("Rejects blank eventId")
    void rejectsBlankEventId() {
        assertThatThrownBy(() -> new LoginHistoryEntry(
                Tenants.DEFAULT_TENANT_ID,
                "", "acc-123", LoginOutcome.SUCCESS,
                null, null, null, null, Instant.now()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId");
    }

    @Test
    @DisplayName("Rejects null outcome")
    void rejectsNullOutcome() {
        assertThatThrownBy(() -> new LoginHistoryEntry(
                Tenants.DEFAULT_TENANT_ID,
                "evt-001", "acc-123", null,
                null, null, null, null, Instant.now()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outcome");
    }

    @Test
    @DisplayName("Rejects null occurredAt")
    void rejectsNullOccurredAt() {
        assertThatThrownBy(() -> new LoginHistoryEntry(
                Tenants.DEFAULT_TENANT_ID,
                "evt-001", "acc-123", LoginOutcome.SUCCESS,
                null, null, null, null, null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("occurredAt");
    }

    @Test
    @DisplayName("Allows null accountId for anonymous login attempts")
    void allowsNullAccountId() {
        LoginHistoryEntry entry = new LoginHistoryEntry(
                Tenants.DEFAULT_TENANT_ID,
                "evt-001", null, LoginOutcome.ATTEMPTED,
                "10.0.0.***", null, null, null, Instant.now()
        );
        assertThat(entry.getAccountId()).isNull();
    }

    @Test
    @DisplayName("TASK-BE-653: siteTenantId is carried; the pre-653 constructor leaves it null; blank → null")
    void siteTenantId() {
        Instant at = Instant.parse("2026-10-24T10:00:00Z");
        LoginHistoryEntry pool = new LoginHistoryEntry("consumer-pool", "ecommerce", "evt-p", "acc-p",
                LoginOutcome.SUCCESS, "10.0.*.*", null, null, null, at);
        LoginHistoryEntry legacy = new LoginHistoryEntry("consumer-pool", "evt-l", "acc-p",
                LoginOutcome.SUCCESS, "10.0.*.*", null, null, null, at);
        LoginHistoryEntry blank = new LoginHistoryEntry("consumer-pool", " ", "evt-b", "acc-p",
                LoginOutcome.SUCCESS, "10.0.*.*", null, null, null, at);

        assertThat(pool.getSiteTenantId()).isEqualTo("ecommerce");
        assertThat(pool.getTenantId()).isEqualTo("consumer-pool");
        assertThat(legacy.getSiteTenantId()).isNull();
        assertThat(blank.getSiteTenantId()).isNull();
    }
}

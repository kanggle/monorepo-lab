package com.example.fanplatform.membership.integration;

import com.example.fanplatform.membership.application.SweepExpiredMembershipsUseCase;
import com.example.fanplatform.membership.domain.membership.Membership;
import com.example.fanplatform.membership.domain.membership.MembershipTier;
import com.example.fanplatform.membership.domain.membership.status.MembershipStatus;
import com.example.fanplatform.membership.infrastructure.jpa.MembershipJpaRepository;
import com.example.fanplatform.membership.infrastructure.jpa.MembershipOutboxJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Expiry sweeper IT (TASK-FAN-BE-014). The scheduler is disabled
 * ({@code expiry-sweep.enabled=false}) so the use case is driven directly to
 * control timing. Verifies: a past-window ACTIVE membership is swept EXACTLY ONCE
 * (one {@code fan.membership.expired} outbox row + marker set), a future-window
 * one is left alone, and a second sweep is a no-op (the marker predicate excludes
 * the already-swept row). The membership keeps {@code status=ACTIVE} (Option B).
 */
@TestPropertySource(properties = "fanplatform.membership.expiry-sweep.enabled=false")
class ExpirySweepIntegrationTest extends MembershipServiceIntegrationBase {

    @Autowired
    SweepExpiredMembershipsUseCase sweepUseCase;

    @Autowired
    MembershipJpaRepository memberships;

    @Autowired
    MembershipOutboxJpaRepository outbox;

    @BeforeEach
    void clean() {
        truncateAll();
    }

    @AfterEach
    void cleanUp() {
        truncateAll();
    }

    private void seedActive(String id, Instant validFrom, Instant validTo) {
        memberships.save(Membership.activate(
                id, "fan-platform", "acc-" + id, MembershipTier.MEMBERS_ONLY,
                validFrom, validTo, 1, "pay-ref", validFrom));
    }

    @Test
    @DisplayName("past-window ACTIVE swept once → one expired outbox row + marker; future untouched; re-sweep no-op")
    void sweepsExpiredOnce() {
        Instant now = Instant.now();
        seedActive("past", now.minus(40, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS));
        seedActive("future", now.minus(1, ChronoUnit.DAYS), now.plus(29, ChronoUnit.DAYS));

        int swept = sweepUseCase.sweep(100);
        assertThat(swept).isEqualTo(1);

        assertThat(outbox.findAll())
                .hasSize(1)
                .allMatch(e -> "fan.membership.expired".equals(e.getEventType())
                        && "past".equals(e.getAggregateId()));
        assertThat(memberships.findById("past")).get()
                .matches(m -> m.getExpiryNotifiedAt() != null, "past is expiry-notified")
                .matches(m -> m.getStatus() == MembershipStatus.ACTIVE,
                        "status stays ACTIVE (no stored EXPIRED)");
        assertThat(memberships.findById("future")).get()
                .matches(m -> m.getExpiryNotifiedAt() == null, "future is not swept");

        // Second sweep: the marker predicate now excludes "past" → no new event.
        assertThat(sweepUseCase.sweep(100)).isZero();
        assertThat(outbox.findAll()).hasSize(1);
    }
}

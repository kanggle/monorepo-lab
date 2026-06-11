package com.example.fanplatform.notification.domain.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTemplateTest {

    @Test
    @DisplayName("WELCOME renders the tier, validity window and plan length")
    void welcomeRendersFromPayload() {
        Instant from = Instant.parse("2026-06-11T00:00:00Z");
        Instant to = Instant.parse("2026-07-11T00:00:00Z");

        NotificationTemplate.RenderedContent c = NotificationTemplate.welcome("PREMIUM", 1, from, to);

        assertThat(c.title()).isEqualTo("Welcome to PREMIUM membership");
        assertThat(c.body())
                .contains("PREMIUM")
                .contains(from.toString())
                .contains(to.toString())
                .contains("1 month")
                .doesNotContain("1 months");
    }

    @Test
    @DisplayName("WELCOME pluralizes the month count")
    void welcomePluralizes() {
        NotificationTemplate.RenderedContent c = NotificationTemplate.welcome(
                "MEMBERS_ONLY", 3, Instant.parse("2026-06-11T00:00:00Z"),
                Instant.parse("2026-09-11T00:00:00Z"));
        assertThat(c.body()).contains("3 months");
    }

    @Test
    @DisplayName("CANCELLATION renders the tier and cancel time, omits reason when null")
    void cancellationWithoutReason() {
        Instant canceledAt = Instant.parse("2026-06-11T12:00:00Z");
        NotificationTemplate.RenderedContent c =
                NotificationTemplate.cancellation("PREMIUM", canceledAt, null);

        assertThat(c.title()).isEqualTo("Your PREMIUM membership was canceled");
        assertThat(c.body())
                .contains("PREMIUM")
                .contains(canceledAt.toString())
                .doesNotContain("Reason:");
    }

    @Test
    @DisplayName("CANCELLATION includes the reason when present")
    void cancellationWithReason() {
        NotificationTemplate.RenderedContent c = NotificationTemplate.cancellation(
                "PREMIUM", Instant.parse("2026-06-11T12:00:00Z"), "user requested");
        assertThat(c.body()).contains("Reason: user requested.");
    }

    @Test
    @DisplayName("EXPIRY_REMINDER renders the tier and window end")
    void expiryRendersFromPayload() {
        Instant validTo = Instant.parse("2026-07-11T00:00:00Z");
        NotificationTemplate.RenderedContent c = NotificationTemplate.expiry("MEMBERS_ONLY", validTo);

        assertThat(c.title()).isEqualTo("Your MEMBERS_ONLY membership has expired");
        assertThat(c.body())
                .contains("MEMBERS_ONLY")
                .contains(validTo.toString())
                .contains("Renew");
    }
}

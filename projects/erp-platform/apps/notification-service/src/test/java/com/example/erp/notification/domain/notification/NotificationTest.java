package com.example.erp.notification.domain.notification;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Notification mark-read idempotency (readAt preserved on re-mark). */
class NotificationTest {

    private final Instant created = Instant.parse("2026-06-05T10:00:00Z");

    private Notification fresh() {
        return Notification.create("ntf-1", "erp", "emp-1", NotificationType.APPROVAL_SUBMITTED,
                "t", "b", SourceRef.approval("appr-1"), created);
    }

    @Test
    void freshNotificationIsUnreadWithNoReadAt() {
        Notification n = fresh();
        assertThat(n.read()).isFalse();
        assertThat(n.readAt()).isEmpty();
    }

    @Test
    void markReadSetsReadAtOnce() {
        Notification n = fresh();
        Instant first = Instant.parse("2026-06-05T11:00:00Z");
        n.markRead(first);
        assertThat(n.read()).isTrue();
        assertThat(n.readAt()).contains(first);

        // Re-mark is a no-op — original readAt preserved (idempotent).
        Instant second = Instant.parse("2026-06-05T12:00:00Z");
        n.markRead(second);
        assertThat(n.readAt()).contains(first);
    }
}

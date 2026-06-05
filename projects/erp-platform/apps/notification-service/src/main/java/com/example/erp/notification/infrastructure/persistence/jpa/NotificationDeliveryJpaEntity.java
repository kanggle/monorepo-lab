package com.example.erp.notification.infrastructure.persistence.jpa;

import com.example.erp.notification.domain.delivery.DeliveryChannel;
import com.example.erp.notification.domain.delivery.DeliveryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** {@code notification_delivery} row (Category C structure; {@code version} for T5). */
@Entity
@Table(name = "notification_delivery")
@Getter
@Setter
@NoArgsConstructor
public class NotificationDeliveryJpaEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "notification_id", nullable = false, length = 64)
    private String notificationId;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16)
    private DeliveryChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private DeliveryStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "scheduled_retry_at")
    private Instant scheduledRetryAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

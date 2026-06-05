package com.example.erp.notification.infrastructure.persistence.jpa;

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

/** {@code notification} row. The {@code read}/{@code read_at} pair is the single mutable state. */
@Entity
@Table(name = "notification")
@Getter
@Setter
@NoArgsConstructor
public class NotificationJpaEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "recipient_id", nullable = false, length = 64)
    private String recipientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private com.example.erp.notification.domain.notification.NotificationType type;

    @Column(name = "title", nullable = false, length = 256)
    private String title;

    @Column(name = "body", nullable = false, length = 2000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private com.example.erp.notification.domain.notification.SourceRef.SourceType sourceType;

    @Column(name = "source_id", nullable = false, length = 64)
    private String sourceId;

    // 'read' is a MySQL reserved word — store as is_read (the API/DTO field
    // stays 'read'; this is a column-name detail only).
    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;
}

package com.example.notification.adapter.out.persistence.entity;

import com.example.notification.domain.model.NotificationChannel;
import com.example.notification.domain.model.TemplateType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_templates",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_template_tenant_type_channel",
                columnNames = {"tenant_id", "type", "channel"}))
@Getter
@Setter
@NoArgsConstructor
public class NotificationTemplateJpaEntity {

    @Id
    @Column(name = "template_id")
    private String templateId;

    @Column(name = "tenant_id", nullable = false, updatable = false, length = 64)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TemplateType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private NotificationChannel channel;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

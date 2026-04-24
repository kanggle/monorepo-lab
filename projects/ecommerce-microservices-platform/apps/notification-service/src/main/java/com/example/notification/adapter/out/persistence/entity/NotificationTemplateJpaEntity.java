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
        uniqueConstraints = @UniqueConstraint(columnNames = {"type", "channel"}))
@Getter
@Setter
@NoArgsConstructor
public class NotificationTemplateJpaEntity {

    @Id
    @Column(name = "template_id")
    private String templateId;

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

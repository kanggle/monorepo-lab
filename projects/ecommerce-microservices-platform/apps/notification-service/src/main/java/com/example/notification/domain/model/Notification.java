package com.example.notification.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class Notification {

    private String notificationId;
    private String userId;
    private NotificationChannel channel;
    private String subject;
    private String body;
    private NotificationStatus status;
    private String eventId;
    private int retryCount;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;

    private Notification() {
    }

    public static Notification create(String userId, NotificationChannel channel,
                                       String subject, String body, String eventId) {
        Notification notification = new Notification();
        notification.notificationId = UUID.randomUUID().toString();
        notification.userId = userId;
        notification.channel = channel;
        notification.subject = subject;
        notification.body = body;
        notification.status = NotificationStatus.PENDING;
        notification.eventId = eventId;
        notification.retryCount = 0;
        notification.createdAt = LocalDateTime.now();
        return notification;
    }

    public static Notification reconstitute(String notificationId, String userId,
                                             NotificationChannel channel, String subject,
                                             String body, NotificationStatus status,
                                             String eventId, int retryCount,
                                             LocalDateTime sentAt, LocalDateTime createdAt) {
        Notification notification = new Notification();
        notification.notificationId = notificationId;
        notification.userId = userId;
        notification.channel = channel;
        notification.subject = subject;
        notification.body = body;
        notification.status = status;
        notification.eventId = eventId;
        notification.retryCount = retryCount;
        notification.sentAt = sentAt;
        notification.createdAt = createdAt;
        return notification;
    }

    public void markSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = NotificationStatus.FAILED;
        this.retryCount++;
    }

    public boolean canRetry(int maxRetries) {
        return this.status == NotificationStatus.FAILED && this.retryCount < maxRetries;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public String getUserId() {
        return userId;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public String getEventId() {
        return eventId;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

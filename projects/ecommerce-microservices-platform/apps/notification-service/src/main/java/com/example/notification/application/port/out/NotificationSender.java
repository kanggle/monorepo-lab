package com.example.notification.application.port.out;

import com.example.notification.domain.model.NotificationChannel;

public interface NotificationSender {
    void send(String recipient, String subject, String body);
    NotificationChannel supportedChannel();
}

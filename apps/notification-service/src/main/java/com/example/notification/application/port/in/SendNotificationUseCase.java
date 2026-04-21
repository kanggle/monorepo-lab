package com.example.notification.application.port.in;

import com.example.notification.application.command.SendNotificationCommand;

public interface SendNotificationUseCase {
    void sendNotification(SendNotificationCommand command);
}

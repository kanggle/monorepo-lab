package com.example.notification.application.port.out;

import com.example.notification.application.page.PageQuery;
import com.example.notification.application.page.PageResult;
import com.example.notification.domain.model.NotificationChannel;
import com.example.notification.domain.model.NotificationTemplate;
import com.example.notification.domain.model.TemplateType;

import java.util.Optional;

public interface TemplateRepository {
    NotificationTemplate save(NotificationTemplate template);
    Optional<NotificationTemplate> findById(String templateId);
    Optional<NotificationTemplate> findByTypeAndChannel(TemplateType type, NotificationChannel channel);
    boolean existsByTypeAndChannel(TemplateType type, NotificationChannel channel);
    PageResult<NotificationTemplate> findAll(PageQuery pageQuery);
}

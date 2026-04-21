package com.example.notification.domain.model;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationTemplate {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    private String templateId;
    private TemplateType type;
    private NotificationChannel channel;
    private String subject;
    private String body;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private NotificationTemplate() {
    }

    public static NotificationTemplate create(TemplateType type, NotificationChannel channel,
                                               String subject, String body) {
        NotificationTemplate template = new NotificationTemplate();
        template.templateId = UUID.randomUUID().toString();
        template.type = type;
        template.channel = channel;
        template.subject = subject;
        template.body = body;
        template.createdAt = LocalDateTime.now();
        template.updatedAt = LocalDateTime.now();
        return template;
    }

    public static NotificationTemplate reconstitute(String templateId, TemplateType type,
                                                     NotificationChannel channel,
                                                     String subject, String body,
                                                     LocalDateTime createdAt,
                                                     LocalDateTime updatedAt) {
        NotificationTemplate template = new NotificationTemplate();
        template.templateId = templateId;
        template.type = type;
        template.channel = channel;
        template.subject = subject;
        template.body = body;
        template.createdAt = createdAt;
        template.updatedAt = updatedAt;
        return template;
    }

    public void update(String subject, String body) {
        this.subject = subject;
        this.body = body;
        this.updatedAt = LocalDateTime.now();
    }

    public String renderSubject(Map<String, String> variables) {
        return replacePlaceholders(this.subject, variables);
    }

    public String renderBody(Map<String, String> variables) {
        return replacePlaceholders(this.body, variables);
    }

    private String replacePlaceholders(String template, Map<String, String> variables) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = variables.getOrDefault(key, "");
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public String getTemplateId() {
        return templateId;
    }

    public TemplateType getType() {
        return type;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}

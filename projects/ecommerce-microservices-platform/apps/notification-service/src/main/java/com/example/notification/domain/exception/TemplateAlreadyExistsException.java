package com.example.notification.domain.exception;

public class TemplateAlreadyExistsException extends RuntimeException {
    public TemplateAlreadyExistsException(String type, String channel) {
        super("Template already exists for type=" + type + ", channel=" + channel);
    }
}

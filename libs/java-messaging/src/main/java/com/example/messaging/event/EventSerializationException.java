package com.example.messaging.event;

public class EventSerializationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EventSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}

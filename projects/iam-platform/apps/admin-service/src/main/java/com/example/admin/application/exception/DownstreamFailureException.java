package com.example.admin.application.exception;

public class DownstreamFailureException extends RuntimeException {
    public DownstreamFailureException(String message) {
        super(message);
    }

    public DownstreamFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}

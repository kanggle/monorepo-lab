package com.example.admin.application.exception;

public class ReasonRequiredException extends RuntimeException {
    public ReasonRequiredException() {
        super("X-Operator-Reason header or reason field is required");
    }
}

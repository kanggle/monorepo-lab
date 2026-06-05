package com.example.membership.application.exception;

public class PlanNotFoundException extends RuntimeException {
    public PlanNotFoundException(String planLevel) {
        super("Plan not found: " + planLevel);
    }
}

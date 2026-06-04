package com.example.erp.approval.domain.route;

import java.util.Objects;

/**
 * The single-stage route's approver identity (an employee id) — E3 / E6.
 * Pure value object — no framework imports.
 */
public record Approver(String approverId) {

    public Approver {
        Objects.requireNonNull(approverId, "approverId");
        if (approverId.isBlank()) {
            throw new IllegalArgumentException("approverId must not be blank");
        }
    }

    public boolean matches(String principalId) {
        return approverId.equals(principalId);
    }
}

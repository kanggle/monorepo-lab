package com.example.erp.approval.domain.authorization;

/**
 * Coarse access intent evaluated by the {@code AuthorizationPort} (E6). READ
 * covers list / detail / inbox; WRITE covers create + the four transitions.
 * Pure Java — no framework imports.
 */
public enum RequiredScope {
    READ,
    WRITE
}

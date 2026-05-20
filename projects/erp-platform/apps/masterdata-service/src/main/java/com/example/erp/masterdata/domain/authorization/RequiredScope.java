package com.example.erp.masterdata.domain.authorization;

/**
 * Coarse scope required by a use case (v1 surface — per-aggregate matrix is
 * forward-decl for v2 `permission-service`, architecture.md § Authorization
 * matrix + Data scope).
 */
public enum RequiredScope {
    READ,
    WRITE
}

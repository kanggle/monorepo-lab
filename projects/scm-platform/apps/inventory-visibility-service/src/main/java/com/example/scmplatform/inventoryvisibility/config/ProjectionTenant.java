package com.example.scmplatform.inventoryvisibility.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The tenant that event-driven writes land under (TASK-MONO-760).
 *
 * <p>wms inventory events and the 3PL inbound-expected event carry no tenant of their
 * own, so their consumers project into this one value — and the staleness batch scans
 * it, so it sees the nodes those consumers created. Reads, by contrast, are scoped by the
 * caller token's {@code tenant_id} ({@code TenantClaimExtractor}). With the default
 * {@code scm}, an operator whose active tenant (ADR-MONO-020) is another customer reads
 * an empty projection; a deployment whose operators work under one customer tenant sets
 * this to that tenant (the AWS demo sets {@code demo-corp}).
 *
 * <p>🔴 Before this bean the value was a {@code "scm"} literal in four consumers, plus the
 * staleness batch borrowing {@code scmplatform.oauth2.required-tenant-id} — five places
 * that had to agree. Keep it in one place.
 */
@Component
public class ProjectionTenant {

    private final String id;

    public ProjectionTenant(@Value("${inventory-visibility.projection-tenant-id:scm}") String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("inventory-visibility.projection-tenant-id must not be blank");
        }
        this.id = id.trim();
    }

    public String id() {
        return id;
    }
}

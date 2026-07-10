package com.example.admin.infrastructure.persistence.rbac;

import com.example.admin.application.orgnode.CeilingView;
import com.example.admin.application.port.OrgNodePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TASK-BE-492 (ADR-MONO-047 D5) — the <b>fail-closed</b> boundary between the org-node
 * authority (account-service) and admin-service's authorization decisions.
 *
 * <p>Two reads sit on a permission-check path, and both must narrow reach on failure:
 * <ul>
 *   <li>{@link #subtreeTenantIdsFailClosed} — expands a node-scoped grant. A failure
 *       contributes the <b>EMPTY set</b>: never {@code '*'}, never "all tenants". A
 *       company-wide admin whose subtree cannot be resolved simply loses reach; it must
 *       never be silently promoted to platform-wide.</li>
 *   <li>{@link #effectiveCeilingFailClosed} — bounds an {@code ORG_ADMIN} grant. A failure
 *       resolves to {@link CeilingView#failClosed()} ({@code BOUNDED([])}, permitting
 *       nothing): never to {@code UNBOUNDED}.</li>
 * </ul>
 *
 * <p><b>Only successes are cached, and only briefly.</b> Caching a failure would turn a
 * transient outage into a lasting permissive (or, worse, a lasting wrong) answer; the whole
 * point of failing closed is defeated if the closed answer sticks around after the
 * authority recovers, and a permissive answer must never be cached at all. On failure the
 * entry is evicted, so the next request retries against the authority. Entries past their
 * TTL are never served — a stale success is as wrong as a cached failure.
 *
 * <p>The TTL keeps a burst of gated mutations from issuing one round-trip per call while
 * remaining short enough that a revoked node membership takes effect promptly (grant
 * revocation itself is immediate — it deletes the {@code admin_operator_roles} row, which
 * is read from the DB on every request and is not cached here).
 */
@Slf4j
@Component
public class OrgNodeSubtreeResolver {

    private final OrgNodePort orgNodePort;
    private final long ttlMillis;
    private final Map<String, CachedSubtree> cache = new ConcurrentHashMap<>();

    public OrgNodeSubtreeResolver(OrgNodePort orgNodePort,
                                  @Value("${admin.org-node.subtree-cache-ttl-ms:5000}") long ttlMillis) {
        this.orgNodePort = orgNodePort;
        this.ttlMillis = ttlMillis;
    }

    /**
     * @return the tenants under {@code orgNodeId}'s subtree, or the EMPTY set when the node
     *         is unknown or the authority is unreachable. Never {@code '*'}.
     */
    public Set<String> subtreeTenantIdsFailClosed(String orgNodeId) {
        if (orgNodeId == null || orgNodeId.isBlank()) {
            return Set.of();
        }
        long now = System.currentTimeMillis();
        CachedSubtree hit = cache.get(orgNodeId);
        if (hit != null && hit.expiresAtMillis() > now) {
            return hit.tenantIds();
        }
        try {
            List<String> resolved = orgNodePort.subtreeTenantIds(orgNodeId);
            Set<String> tenantIds = resolved == null ? Set.of() : Set.copyOf(resolved);
            cache.put(orgNodeId, new CachedSubtree(tenantIds, now + ttlMillis));
            return tenantIds;
        } catch (RuntimeException ex) {
            // fail-CLOSED. Evict rather than cache: the failure must not outlive the outage,
            // and a stale success must not be served in its place.
            cache.remove(orgNodeId);
            log.error("fail-closed: org-node subtree resolution failed, contributing the EMPTY "
                    + "tenant set (never '*', never all-tenants). orgNodeId={}", orgNodeId, ex);
            return Set.of();
        }
    }

    /**
     * @return {@code effectiveCeiling(orgNodeId)}, or {@link CeilingView#failClosed()} when
     *         the node is unknown or the authority is unreachable. Never {@code UNBOUNDED}.
     */
    public CeilingView effectiveCeilingFailClosed(String orgNodeId) {
        if (orgNodeId == null || orgNodeId.isBlank()) {
            return CeilingView.failClosed();
        }
        try {
            CeilingView ceiling = orgNodePort.effectiveCeiling(orgNodeId);
            return ceiling == null ? CeilingView.failClosed() : ceiling;
        } catch (RuntimeException ex) {
            log.error("fail-closed: org-node effective-ceiling resolution failed, denying the "
                    + "grant (BOUNDED([]), never UNBOUNDED). orgNodeId={}", orgNodeId, ex);
            return CeilingView.failClosed();
        }
    }

    /** Test seam: drop every cached subtree. */
    public void invalidateAll() {
        cache.clear();
    }

    private record CachedSubtree(Set<String> tenantIds, long expiresAtMillis) {}
}

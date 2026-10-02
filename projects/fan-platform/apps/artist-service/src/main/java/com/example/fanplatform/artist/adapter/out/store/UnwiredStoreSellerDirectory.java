package com.example.fanplatform.artist.adapter.out.store;

import com.example.fanplatform.artist.application.exception.StoreSellerLookupUnavailableException;
import com.example.fanplatform.artist.application.port.out.StoreSellerDirectory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The only {@link StoreSellerDirectory} today — and it answers «cannot verify» to every
 * question, so every attempt to LINK a store seller is refused (503
 * {@code STORE_SELLER_LOOKUP_UNAVAILABLE}). Clearing a link needs no lookup and works.
 *
 * <h2>Why there is no HTTP adapter here (TASK-MONO-748 Hard Stop)</h2>
 *
 * The fan → store seller lookup crosses a project boundary and no existing transport
 * can carry it without a decision nobody has made yet:
 * <ul>
 *   <li>The store's seller data lives in ecommerce {@code product-service}, which has
 *       <b>no JWT-validating surface at all</b> — it trusts the ecommerce gateway's
 *       {@code X-Tenant-Id} / {@code X-User-Role} headers, and its only seller reads are
 *       {@code /api/admin/sellers/**} (operator plane, {@code ECOMMERCE_OPERATOR}).</li>
 *   <li>artist-service holds <b>no IdP client</b>. Reaching that data with a workload
 *       token needs a new {@code client_credentials} registration, a seller-read scope,
 *       and a token whose {@code tenant_id} is the store tenant (the RFC 8693
 *       assume-tenant path, {@code WorkloadTenantCatalog}) — an IdP registration plus a
 *       permission-catalog change, which this ticket may not invent (TASK-MONO-726 needed
 *       the owner's approval for exactly that kind of registration).</li>
 * </ul>
 *
 * <p>So the verification <b>semantics</b> are implemented and tested in the application
 * service against the port, and this bean holds the fail-closed side until the transport
 * is decided. 🔴 It must never be replaced by a permissive stub: an always-«ACTIVE»
 * directory is exactly Failure Scenario 2 («조회 장애 때 검증 없이 저장»).
 */
@Component
public class UnwiredStoreSellerDirectory implements StoreSellerDirectory {

    @Override
    public Optional<String> findStatus(String sellerId) {
        throw new StoreSellerLookupUnavailableException(
                "store seller lookup is not wired (cross-project transport undecided — "
                        + "TASK-MONO-748); the link was not saved");
    }
}

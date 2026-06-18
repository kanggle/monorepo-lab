package com.example.product.application.service;

import com.example.product.domain.exception.SellerNotFoundException;
import com.example.product.domain.model.Seller;
import com.example.product.domain.repository.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * SHORT-transaction persistence collaborator for {@link RegisterSellerService} (ADR-MONO-042,
 * TASK-BE-402 M1 fix). Each DB mutation is its own {@code @Transactional} unit so the
 * synchronous account-service provisioning HTTP call (read-timeout up to 10s) runs OUTSIDE
 * any DB transaction — it must not hold the {@code sellers} row lock + the DB connection for
 * the whole IAM timeout on a slow/hanging account-service (which would risk connection-pool
 * exhaustion). Mirrors the admin-service blueprint where the provisioning call is not wrapped
 * by the seller-row DB tx.
 *
 * <p>This is a SEPARATE Spring bean (not a self-invocation in {@link RegisterSellerService})
 * so the {@code @Transactional} proxy actually applies — calling a {@code @Transactional}
 * method on {@code this} would bypass the proxy and silently run without a transaction.
 *
 * <p>The current tenant is carried by the {@code TenantContext} ThreadLocal, which stays bound
 * on the request thread across the short txns and the intervening HTTP call.
 */
@Component
@RequiredArgsConstructor
class SellerLifecyclePersistence {

    private final SellerRepository sellerRepository;

    /** Persist a freshly-registered seller (PENDING_PROVISIONING). Short tx. */
    @Transactional
    Seller save(Seller seller) {
        return sellerRepository.save(seller);
    }

    /** Apply a mutated seller's lifecycle fields onto the existing row. Short tx. */
    @Transactional
    Seller update(Seller seller) {
        return sellerRepository.update(seller);
    }

    /** Look up a seller by id within the current tenant (read tx). */
    @Transactional(readOnly = true)
    Optional<Seller> findById(String sellerId) {
        return sellerRepository.findById(sellerId);
    }

    /** Look up a seller by id or throw {@link SellerNotFoundException} (read tx). */
    @Transactional(readOnly = true)
    Seller getOrThrow(String sellerId) {
        return sellerRepository.findById(sellerId)
                .orElseThrow(() -> new SellerNotFoundException(sellerId));
    }

    /** Idempotently ensure the per-tenant default seller exists (D8 anchor). Short tx. */
    @Transactional
    Seller ensureDefaultSeller() {
        return sellerRepository.ensureDefaultSeller();
    }
}

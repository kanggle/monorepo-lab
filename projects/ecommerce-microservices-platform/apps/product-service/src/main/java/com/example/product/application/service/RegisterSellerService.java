package com.example.product.application.service;

import com.example.product.application.command.RegisterSellerCommand;
import com.example.product.domain.model.Seller;
import com.example.product.domain.repository.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Minimal seller lifecycle use case (ADR-MONO-030 Step 3 §3.1) — registers a
 * seller (ACTIVE) within the current tenant and guarantees the per-tenant default
 * seller exists. Onboarding/settlement are out of scope (Step 4).
 */
@Service
@RequiredArgsConstructor
public class RegisterSellerService {

    private final SellerRepository sellerRepository;

    @Transactional
    public String register(RegisterSellerCommand command) {
        Seller seller = Seller.register(command.sellerId(), command.displayName());
        return sellerRepository.save(seller).getSellerId();
    }

    /**
     * Idempotently ensures the per-tenant default seller exists (D8 degradation
     * anchor, AC-5). Safe to call before attributing legacy/default-tenant products.
     */
    @Transactional
    public String ensureDefaultSeller() {
        return sellerRepository.ensureDefaultSeller().getSellerId();
    }
}

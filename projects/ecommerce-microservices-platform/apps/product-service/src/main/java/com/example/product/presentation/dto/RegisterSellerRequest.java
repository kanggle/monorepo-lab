package com.example.product.presentation.dto;

import com.example.product.application.command.RegisterSellerCommand;
import jakarta.validation.constraints.NotBlank;

/**
 * OPERATOR request to register a marketplace seller within the current tenant
 * (ADR-MONO-030 Step 3 §3.1). The owning {@code tenant_id} is derived from the
 * request context (gateway {@code X-Tenant-Id}), not the body.
 */
public record RegisterSellerRequest(
        @NotBlank(message = "sellerId는 필수입니다") String sellerId,
        @NotBlank(message = "displayName은 필수입니다") String displayName
) {
    public RegisterSellerCommand toCommand() {
        return new RegisterSellerCommand(sellerId, displayName);
    }
}

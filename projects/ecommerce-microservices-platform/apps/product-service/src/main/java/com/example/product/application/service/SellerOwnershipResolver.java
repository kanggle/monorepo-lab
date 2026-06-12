package com.example.product.application.service;

import com.example.product.domain.model.Seller;
import com.example.product.domain.seller.SellerScopeContext;
import org.springframework.stereotype.Component;

/**
 * Resolves the owning {@code seller_id} for a product at register time
 * (ADR-MONO-030 Step 3 §3.2). Resolution order (first non-blank wins):
 *
 * <ol>
 *   <li><b>explicit request {@code sellerId}</b> — a tenant operator registering
 *       on behalf of a specific seller;</li>
 *   <li><b>restricted seller-scope claim</b> — a seller-scoped OPERATOR can only
 *       register under its own seller (the {@code X-Seller-Scope} header);</li>
 *   <li><b>tenant default seller</b> — standalone / single-seller degradation
 *       (D8, AC-5).</li>
 * </ol>
 *
 * <p>This is an attribution decision, not an authorization decision — the slice
 * trusts the gateway-forwarded scope (task §D). A blank/{@code '*'} scope with no
 * explicit seller resolves to the default seller (net-zero with the
 * pre-marketplace single-seller behaviour).
 */
@Component
public class SellerOwnershipResolver {

    /**
     * @param requestSellerId the optional {@code sellerId} from the register request
     * @return the seller_id the product will be owned by (never blank)
     */
    public String resolveForRegister(String requestSellerId) {
        if (requestSellerId != null && !requestSellerId.isBlank()) {
            return requestSellerId.trim();
        }
        if (SellerScopeContext.isRestricted()) {
            return SellerScopeContext.currentSellerScope();
        }
        return Seller.DEFAULT_SELLER_ID;
    }
}

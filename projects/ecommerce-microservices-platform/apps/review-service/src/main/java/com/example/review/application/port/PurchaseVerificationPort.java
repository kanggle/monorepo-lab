package com.example.review.application.port;

import java.util.UUID;

public interface PurchaseVerificationPort {

    boolean hasUserPurchasedProduct(UUID userId, UUID productId);
}

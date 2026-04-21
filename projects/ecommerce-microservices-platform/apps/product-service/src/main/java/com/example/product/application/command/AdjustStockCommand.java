package com.example.product.application.command;

import java.util.UUID;

public record AdjustStockCommand(UUID productId, UUID variantId, int quantity, String reason) {}

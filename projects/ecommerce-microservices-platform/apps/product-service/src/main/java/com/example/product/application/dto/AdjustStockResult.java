package com.example.product.application.dto;

import java.util.UUID;

public record AdjustStockResult(UUID variantId, int currentStock) {}

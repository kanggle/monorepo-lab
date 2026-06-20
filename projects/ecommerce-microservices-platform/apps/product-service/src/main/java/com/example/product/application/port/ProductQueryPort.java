package com.example.product.application.port;

import com.example.product.application.dto.ProductListResult;
import com.example.product.domain.model.ProductStatus;

import java.util.UUID;

public interface ProductQueryPort {

    ProductListResult findSummaries(UUID categoryId, ProductStatus status, String name, int page, int size);
}

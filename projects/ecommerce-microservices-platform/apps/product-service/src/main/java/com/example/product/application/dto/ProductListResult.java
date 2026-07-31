package com.example.product.application.dto;

import com.example.common.page.PageResult;

import java.util.List;

/**
 * Thin domain-specific wrapper composing the shared {@link PageResult}
 * (ADR-MONO-058 D3 — TASK-BE-567). Kept as a concrete (non-generic) record rather
 * than having {@code QueryProductService.findAll} return {@code PageResult<ProductSummary>}
 * directly, because that method is {@code @Cacheable} through Redis +
 * {@code GenericJackson2JsonRedisSerializer} (see {@code CacheConfig}): a concrete
 * record round-trips through that serializer's default-typing exactly as it did
 * before this change, whereas caching a raw parameterized {@code PageResult<T>} root
 * value is unproven for this cache's (de)serialization path and not worth risking on
 * a pure-adoption refactor. No other adopter in this project caches its list result.
 */
public record ProductListResult(PageResult<ProductSummary> pageResult) {
    public List<ProductSummary> content() {
        return pageResult.content();
    }

    public int page() {
        return pageResult.page();
    }

    public int size() {
        return pageResult.size();
    }

    public long totalElements() {
        return pageResult.totalElements();
    }

    public int totalPages() {
        return pageResult.totalPages();
    }
}

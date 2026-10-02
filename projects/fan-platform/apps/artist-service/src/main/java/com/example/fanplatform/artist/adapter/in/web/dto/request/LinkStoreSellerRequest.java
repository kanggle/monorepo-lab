package com.example.fanplatform.artist.adapter.in.web.dto.request;

import jakarta.validation.constraints.Size;

/**
 * {@code PATCH /api/agencies/{id}/store-seller} — {@code storeSellerId: null} (or an
 * absent key) clears the link; a value is verified against the store before it is
 * saved (ADR-MONO-079 D2, TASK-MONO-748 AC-3).
 */
public record LinkStoreSellerRequest(
        @Size(min = 1, max = 64) String storeSellerId
) {}

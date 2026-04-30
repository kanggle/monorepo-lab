package com.example.admin.presentation.dto;

import java.time.Instant;
import java.util.List;

/**
 * Response body for {@code POST /api/admin/auth/2fa/enroll}.
 * Recovery codes are returned in plaintext exactly once; the server stores
 * only Argon2id hashes.
 */
public record TotpEnrollResponse(
        String otpauthUri,
        List<String> recoveryCodes,
        Instant enrolledAt,
        String bootstrapToken,
        long bootstrapTokenTtlSeconds
) {}

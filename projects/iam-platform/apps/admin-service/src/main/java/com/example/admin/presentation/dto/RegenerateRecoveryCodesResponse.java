package com.example.admin.presentation.dto;

import java.util.List;

/**
 * Response body for {@code POST /api/admin/auth/2fa/recovery-codes/regenerate}.
 * Carries 10 plain-text recovery codes (one-time exposure). The server retains
 * only Argon2id hashes after the response is written.
 */
public record RegenerateRecoveryCodesResponse(List<String> recoveryCodes) {
}

package com.example.auth.presentation.dto;

/**
 * TASK-BE-386 (ADR-MONO-036 P4, M4): result of a credential identity backfill batch.
 *
 * @param requested how many (accountId, identityId) pairs were submitted
 * @param updated   how many credential rows were actually assigned an identity_id
 *                  (rows already linked, or with no matching credential, count 0 —
 *                  idempotent / net-zero)
 */
public record BackfillCredentialIdentityResponse(int requested, int updated) {
}

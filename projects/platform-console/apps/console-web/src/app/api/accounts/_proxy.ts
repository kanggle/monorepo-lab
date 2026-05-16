import { NextResponse } from 'next/server';
import { z } from 'zod';
import { ApiError, AccountsUnavailableError } from '@/shared/api/errors';
import { logger, newRequestId } from '@/shared/lib/logger';

/**
 * Shared error → HTTP mapping for the accounts mutation proxy routes
 * (console-integration-contract § 2.4.1 / § 2.5). Centralised so every
 * destructive op maps identically:
 *   - 401/403 → forced re-login (client API client refresh→login)
 *   - 400 NO_ACTIVE_TENANT → tenant gate
 *   - 400/404/409/422 producer errors → inline actionable (passthrough)
 *   - 503/timeout/network → 503 degrade (accounts section only)
 *
 * The reason/idempotency-key are NEVER fabricated here — the client must
 * supply a non-empty reason (the reason-capture gate); the api layer is the
 * fail-safe. No token / PII is logged.
 */

export const MutationBodySchema = z.object({
  reason: z.string(),
  ticketId: z.string().optional(),
  idempotencyKey: z.string().min(1),
});
export type MutationBody = z.infer<typeof MutationBodySchema>;

export const BulkLockBodySchema = z.object({
  accountIds: z.array(z.string()).min(1),
  reason: z.string(),
  ticketId: z.string().optional(),
  idempotencyKey: z.string().min(1),
});
export type BulkLockBody = z.infer<typeof BulkLockBodySchema>;

export function mapError(err: unknown, requestId: string): NextResponse {
  if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
    return NextResponse.json(
      { code: err.code, message: 'session expired' },
      { status: err.status },
    );
  }
  if (err instanceof ApiError && err.code === 'NO_ACTIVE_TENANT') {
    return NextResponse.json(
      { code: 'NO_ACTIVE_TENANT', message: 'no active tenant selected' },
      { status: 400 },
    );
  }
  if (err instanceof ApiError) {
    // 400 STATE_TRANSITION_INVALID / REASON_REQUIRED / VALIDATION_ERROR,
    // 404 ACCOUNT_NOT_FOUND, 409 IDEMPOTENCY_KEY_CONFLICT,
    // 422 BATCH_SIZE_EXCEEDED → inline actionable (passthrough, no crash).
    return NextResponse.json(
      { code: err.code, message: err.message },
      { status: err.status },
    );
  }
  if (err instanceof AccountsUnavailableError) {
    logger.warn('accounts_mutation_proxy_degraded', {
      requestId,
      reason: err.reason,
    });
    return NextResponse.json(
      { code: err.code, message: 'accounts unavailable' },
      { status: 503 },
    );
  }
  logger.error('accounts_mutation_proxy_error', { requestId });
  return NextResponse.json(
    { code: 'DOWNSTREAM_ERROR', message: 'accounts unavailable' },
    { status: 503 },
  );
}

export function badRequest(): NextResponse {
  return NextResponse.json(
    { code: 'VALIDATION_ERROR', message: 'invalid request body' },
    { status: 422 },
  );
}

export { newRequestId };

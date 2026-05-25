import { NextResponse } from 'next/server';
import { z } from 'zod';
import { ApiError, AccountsUnavailableError } from '@/shared/api/errors';
import { makeProxyErrorMapper } from '@/shared/api/proxy-factory';
import { newRequestId } from '@/shared/lib/logger';

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

export const mapError = makeProxyErrorMapper(
  'accounts',
  AccountsUnavailableError,
  [
    // NO_ACTIVE_TENANT: tenant gate — fail-closed before any upstream call.
    (err) => {
      if (err instanceof ApiError && err.code === 'NO_ACTIVE_TENANT') {
        return NextResponse.json(
          { code: 'NO_ACTIVE_TENANT', message: 'no active tenant selected' },
          { status: 400 },
        );
      }
      return null;
    },
  ],
);

export function badRequest(): NextResponse {
  return NextResponse.json(
    { code: 'VALIDATION_ERROR', message: 'invalid request body' },
    { status: 422 },
  );
}

export { newRequestId };

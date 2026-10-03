import { NextResponse } from 'next/server';
import type { z } from 'zod';
import { FanUnavailableError } from '@/shared/api/errors';
import { makeProxyErrorMapper } from '@/shared/api/proxy-factory';
import { newRequestId } from '@/shared/lib/logger';

/**
 * Shared error → HTTP mapping + body validation for the fan-directory same-origin routes
 * (TASK-MONO-751 — ADR-MONO-079 D4-A; ADR-MONO-081: the console's proxies live in
 * console-web's own server, not console-bff).
 *
 *   - 401 → 401 (the client api-client triggers a whole-session re-login).
 *   - 403 → 403 (e.g. `TENANT_FORBIDDEN` — the token is not `fan-platform`).
 *   - 400 / 404 / 409 / 422 → passthrough with the producer `code` (inline, actionable).
 *   - `FanUnavailableError` → 503 **with the producer code** — so
 *     `STORE_SELLER_LOOKUP_UNAVAILABLE` (the link was refused, nothing saved) reaches the
 *     agency screen as itself instead of as a generic outage.
 *
 * The token is attached server-side in `features/fan-directory/api/fan-api.ts`; neither the
 * token nor any directory data is logged.
 */
export const mapFanError = makeProxyErrorMapper('fan', FanUnavailableError);

export { newRequestId };

/** A 422 for a body the console refuses to forward. */
export function badRequest(): NextResponse {
  return NextResponse.json(
    { code: 'VALIDATION_ERROR', message: 'invalid request body' },
    { status: 422 },
  );
}

/** Parses the JSON body with `schema`; `null` when it is not JSON or does not match. */
export async function parseBody<T extends z.ZodTypeAny>(
  req: Request,
  schema: T,
): Promise<z.infer<T> | null> {
  let raw: unknown;
  try {
    raw = await req.json();
  } catch {
    return null;
  }
  const parsed = schema.safeParse(raw);
  return parsed.success ? parsed.data : null;
}

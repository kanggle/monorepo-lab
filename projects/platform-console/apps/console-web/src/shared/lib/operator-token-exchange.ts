import { z } from 'zod';
import { getServerEnv } from '@/shared/config/env';
import { logger, newRequestId } from '@/shared/lib/logger';
import { OperatorExchangeError } from '@/shared/api/errors';

/**
 * Server-only RFC 8693 token exchange: GAP OIDC `platform-console-web`
 * access token  →  admin-service operator token.
 *
 * Authoritative producer contract (do NOT redefine here — consume only):
 *   - `iam/specs/contracts/http/admin-api.md`
 *     § `POST /api/admin/auth/token-exchange` (request / 200 / 400 / 401).
 *   - `iam/specs/services/admin-service/security.md`
 *     § GAP OIDC Subject-Token Validation (producer fail-closed policy).
 *   - Consumer obligation: `console-integration-contract.md` § 2.6
 *     (ADR-MONO-014 D2 re-exchange model; fail-closed mapping).
 *
 * Invariants (security-critical — token boundary):
 *   - Server-only by construction: imported exclusively from the auth route
 *     handlers (`runtime = 'nodejs'`); never reachable from client code
 *     (same posture as `registry-client.ts`, which is also server-only).
 *   - The GAP OIDC token is ONLY the `subject_token` request input; it is
 *     never returned, never logged, never an `/api/admin/**` credential.
 *   - The minted operator token is never logged.
 *   - Re-exchange model: NO operator-refresh token/state — every GAP
 *     refresh re-exchanges (ADR-MONO-014 D2).
 *
 * Resilience (integration-heavy I1 + § 2.6 / § 2.5 parity):
 *   - Hard timeout (`TOKEN_EXCHANGE_TIMEOUT_MS`) via AbortController.
 *   - `401 TOKEN_INVALID` → `OperatorExchangeError('fail_closed')`
 *     (operator not provisioned / subject invalid → forced re-login).
 *   - `400` / `5xx` / timeout / network / unexpected `tokenType`
 *     → `OperatorExchangeError('unavailable')` (session-unavailable; the
 *     console never falls back to the GAP token on the operator boundary —
 *     the exact #569 latent defect this closes).
 */

/** Verbatim per admin-api.md § POST /api/admin/auth/token-exchange. */
const GRANT_TYPE = 'urn:ietf:params:oauth:grant-type:token-exchange';
const SUBJECT_TOKEN_TYPE = 'urn:ietf:params:oauth:token-type:access_token';

const ExchangeResponseSchema = z.object({
  accessToken: z.string().min(1),
  expiresIn: z.number().int().positive(),
  tokenType: z.literal('admin'),
});

export interface OperatorToken {
  /** admin-service operator JWT (`token_type=admin`, `iss=admin-service`). */
  accessToken: string;
  /** Seconds — used as the operator cookie `maxAge`. */
  expiresIn: number;
}

/**
 * Exchanges the GAP OIDC access token for an operator token.
 *
 * @param gapAccessToken the operator's GAP OIDC `platform-console-web`
 *   access token (the `subject_token`). Never logged.
 * @throws OperatorExchangeError — `fail_closed` (401, re-login) or
 *   `unavailable` (400/5xx/timeout/network — no partial authed state).
 */
export async function exchangeForOperatorToken(
  gapAccessToken: string,
): Promise<OperatorToken> {
  const env = getServerEnv();
  const requestId = newRequestId();

  const controller = new AbortController();
  const timer = setTimeout(
    () => controller.abort(),
    env.TOKEN_EXCHANGE_TIMEOUT_MS,
  );

  try {
    const res = await fetch(env.CONSOLE_TOKEN_EXCHANGE_URL, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        'X-Request-Id': requestId,
      },
      // RFC 8693 body — exactly per admin-api.md (no extra fields).
      body: JSON.stringify({
        grant_type: GRANT_TYPE,
        subject_token: gapAccessToken,
        subject_token_type: SUBJECT_TOKEN_TYPE,
      }),
      cache: 'no-store',
      signal: controller.signal,
    });

    if (res.status === 401) {
      const body = (await res.json().catch(() => ({}))) as { code?: string };
      // Producer fail-closed: invalid subject token OR no active operator
      // mapping (not provisioned). NEVER mint / fall back to the GAP token.
      logger.warn('operator_exchange_fail_closed', {
        requestId,
        status: 401,
        code: body.code,
      });
      throw new OperatorExchangeError(
        'fail_closed',
        body.code ?? 'TOKEN_INVALID',
        'operator token exchange rejected (not provisioned / subject invalid)',
      );
    }

    if (!res.ok) {
      // 400 BAD_REQUEST/VALIDATION_ERROR, 5xx, anything else → unavailable.
      const body = (await res.json().catch(() => ({}))) as { code?: string };
      logger.warn('operator_exchange_unavailable', {
        requestId,
        status: res.status,
        code: body.code,
      });
      throw new OperatorExchangeError(
        'unavailable',
        body.code ?? `HTTP_${res.status}`,
        `operator token exchange returned ${res.status}`,
      );
    }

    const parsed = ExchangeResponseSchema.safeParse(await res.json());
    if (!parsed.success) {
      // Unexpected shape / tokenType !== 'admin' → fail-closed unavailable
      // (do NOT store). No token value is logged.
      logger.warn('operator_exchange_bad_shape', { requestId });
      throw new OperatorExchangeError(
        'unavailable',
        'BAD_RESPONSE_SHAPE',
        'operator token exchange returned an unexpected payload',
      );
    }

    logger.info('operator_exchange_ok', {
      requestId,
      expiresIn: parsed.data.expiresIn,
    });
    return {
      accessToken: parsed.data.accessToken,
      expiresIn: parsed.data.expiresIn,
    };
  } catch (err) {
    if (err instanceof OperatorExchangeError) {
      throw err;
    }
    if (err instanceof Error && err.name === 'AbortError') {
      logger.warn('operator_exchange_timeout', {
        requestId,
        timeoutMs: env.TOKEN_EXCHANGE_TIMEOUT_MS,
      });
      throw new OperatorExchangeError(
        'unavailable',
        'TIMEOUT',
        'operator token exchange timed out',
      );
    }
    // Network / DNS / unreachable iam.local — never expose the cause string
    // (may carry the URL but not tokens; still keep it generic + no token).
    logger.error('operator_exchange_error', { requestId });
    throw new OperatorExchangeError(
      'unavailable',
      'NETWORK_ERROR',
      'operator token exchange failed',
    );
  } finally {
    clearTimeout(timer);
  }
}

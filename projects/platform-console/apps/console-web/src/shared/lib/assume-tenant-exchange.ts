import { z } from 'zod';
import { getServerEnv } from '@/shared/config/env';
import { logger, newRequestId } from '@/shared/lib/logger';
import { AssumeTenantError } from '@/shared/api/errors';

/**
 * Server-only RFC 8693 **assume-tenant** token exchange: the operator's base
 * IAM OIDC `platform-console-web` access token  →  a short-lived
 * domain-facing IAM OIDC access token scoped to the **selected** customer
 * tenant (AWS STS AssumeRole analogue). This is the active-tenant switcher's
 * server-side driver (ADR-MONO-020 D4 / § 3.3 step 3).
 *
 * Authoritative producer contract (do NOT redefine here — consume only):
 *   - `iam/specs/contracts/http/auth-api.md`
 *     § Assume-Tenant Exchange (`POST /oauth2/token`,
 *     `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
 *     `audience=<selected tenant>`, RFC 8693 — TASK-BE-327, D2+D3).
 *   - Consumer obligation: `console-integration-contract.md` § 2.7
 *     (active-tenant switcher → assume-tenant flow; fail-closed switch).
 *
 * **Wire-format difference vs the § 2.6 operator exchange** (do NOT copy that
 * shape): this is the SAS `/oauth2/token` endpoint, **form-urlencoded**, with
 * an `audience` parameter and a SAS token response (`access_token`,
 * `expires_in`, `token_type=Bearer`, **no `refresh_token`**) — NOT the admin
 * JSON `{ accessToken, expiresIn, tokenType: 'admin' }` shape.
 *
 * Invariants (security-critical — selected-tenant boundary):
 *   - Server-only by construction: imported exclusively from the
 *     `/api/tenant` switch route + the `/api/auth/refresh` re-assume path
 *     (`runtime = 'nodejs'`); never reachable from client code.
 *   - The base IAM OIDC token is ONLY the `subject_token` request input; it is
 *     never returned, never logged. The minted assumed token is never logged.
 *   - **No fallback to the base token on the selected-tenant boundary**: on
 *     any failure the switch is rejected; the console never serves the base
 *     (login-tenant) token in place of the assumed token for a selected
 *     tenant (the silent wrong-tenant-view defect this closes).
 *   - **No refresh token**: the producer issues none (D2). Re-assume on every
 *     IAM refresh (re-exchange model — mirrors § 2.6).
 *
 * Resilience (integration-heavy I1 + § 2.6 parity):
 *   - Hard timeout (`TOKEN_EXCHANGE_TIMEOUT_MS`) via AbortController.
 *   - `400 invalid_grant` (assignment-denied / subject-invalid / producer's
 *     admin-service leg unavailable — the D2 fail-CLOSED gate)
 *     → `AssumeTenantError('denied')` (switch rejected, prior selection kept).
 *   - `400 invalid_request` (missing/blank `audience`) → `'invalid'`.
 *   - `5xx` / timeout / network / unexpected response shape → `'unavailable'`.
 */

/** Verbatim per auth-api.md § Assume-Tenant Exchange. */
const GRANT_TYPE = 'urn:ietf:params:oauth:grant-type:token-exchange';
const SUBJECT_TOKEN_TYPE = 'urn:ietf:params:oauth:token-type:access_token';

/**
 * SAS token response (assume-tenant). `token_type` MUST be `Bearer`; the grant
 * issues NO `refresh_token` (D2 — the assumed token is short-lived and
 * re-minted on each selection / IAM refresh). Extra fields (`issued_token_type`,
 * `scope`) are tolerated and ignored.
 */
const AssumeResponseSchema = z.object({
  access_token: z.string().min(1),
  expires_in: z.number().int().positive(),
  token_type: z.literal('Bearer'),
});

export interface AssumedToken {
  /** Domain-facing IAM OIDC JWT scoped to the selected tenant
   *  (`tenant_id=<audience>` + `entitled_domains=<audience's ACTIVE subs>`). */
  accessToken: string;
  /** Seconds — used as the assumed-token cookie `maxAge`. */
  expiresIn: number;
}

/** SAS `/oauth2/token` endpoint, derived from the OIDC issuer base (the same
 *  endpoint the refresh route uses). No separate env var — the issuer URL is
 *  the single source (ADR-MONO-020 § 3.3 step 3 — implementer derive choice). */
function assumeTenantUrl(issuerUrl: string): string {
  return `${issuerUrl.replace(/\/$/, '')}/oauth2/token`;
}

/**
 * Exchanges the base IAM OIDC access token for an assumed (tenant-scoped)
 * domain-facing token.
 *
 * @param gapAccessToken the operator's base IAM OIDC `platform-console-web`
 *   access token (the `subject_token`). Never logged.
 * @param selectedTenant the customer tenant to assume (the RFC 8693
 *   `audience`). Must be non-blank.
 * @throws AssumeTenantError — `denied` (assignment gate / subject invalid,
 *   switch rejected), `invalid` (bad audience), or `unavailable`
 *   (5xx/timeout/network — no partial state). Never falls back to the base
 *   token on this boundary.
 */
export async function exchangeForAssumedToken(
  gapAccessToken: string,
  selectedTenant: string,
): Promise<AssumedToken> {
  const env = getServerEnv();
  const requestId = newRequestId();

  // Local pre-validation mirrors the producer `invalid_request` gate — a blank
  // audience can never produce a tenant-scoped token (and must not be sent).
  if (selectedTenant.trim() === '') {
    throw new AssumeTenantError(
      'invalid',
      'AUDIENCE_REQUIRED',
      'assume-tenant requires a non-blank selected tenant',
    );
  }

  const controller = new AbortController();
  const timer = setTimeout(
    () => controller.abort(),
    env.TOKEN_EXCHANGE_TIMEOUT_MS,
  );

  try {
    // RFC 8693 form-urlencoded body — exactly per auth-api.md (no extra fields
    // beyond client_id, which the SAS public client requires).
    const form = new URLSearchParams();
    form.set('grant_type', GRANT_TYPE);
    form.set('subject_token', gapAccessToken);
    form.set('subject_token_type', SUBJECT_TOKEN_TYPE);
    form.set('audience', selectedTenant);
    form.set('client_id', env.OIDC_CLIENT_ID);

    const res = await fetch(assumeTenantUrl(env.OIDC_ISSUER_URL), {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        Accept: 'application/json',
        'X-Request-Id': requestId,
      },
      body: form.toString(),
      cache: 'no-store',
      signal: controller.signal,
    });

    if (res.status === 400) {
      // Distinguish the two producer 400s by the OAuth `error` field.
      // `invalid_grant` = the fail-CLOSED gate (assignment-denied / subject
      // invalid / producer admin-service unavailable) → switch REJECTED.
      // `invalid_request` = bad/blank audience → client request defect.
      const body = (await res.json().catch(() => ({}))) as { error?: string };
      const oauthError = body.error;
      if (oauthError === 'invalid_request') {
        logger.warn('assume_tenant_invalid_request', {
          requestId,
          tenant: selectedTenant,
        });
        throw new AssumeTenantError(
          'invalid',
          'INVALID_REQUEST',
          'assume-tenant rejected (audience missing/malformed)',
        );
      }
      // Default 400 → denied (the assignment fail-closed gate). NEVER mint /
      // fall back to the base token; the prior selection is preserved.
      logger.warn('assume_tenant_denied', {
        requestId,
        tenant: selectedTenant,
        error: oauthError,
      });
      throw new AssumeTenantError(
        'denied',
        'TENANT_ASSIGNMENT_DENIED',
        'assume-tenant rejected (not assigned / subject invalid)',
      );
    }

    if (!res.ok) {
      // 5xx / 401 invalid_client / anything else → unavailable. No fallback.
      logger.warn('assume_tenant_unavailable', {
        requestId,
        tenant: selectedTenant,
        status: res.status,
      });
      throw new AssumeTenantError(
        'unavailable',
        `HTTP_${res.status}`,
        `assume-tenant exchange returned ${res.status}`,
      );
    }

    const parsed = AssumeResponseSchema.safeParse(await res.json());
    if (!parsed.success) {
      // Unexpected shape / token_type !== 'Bearer' → unavailable (do NOT
      // store). No token value is logged.
      logger.warn('assume_tenant_bad_shape', { requestId });
      throw new AssumeTenantError(
        'unavailable',
        'BAD_RESPONSE_SHAPE',
        'assume-tenant exchange returned an unexpected payload',
      );
    }

    logger.info('assume_tenant_ok', {
      requestId,
      tenant: selectedTenant,
      expiresIn: parsed.data.expires_in,
    });
    return {
      accessToken: parsed.data.access_token,
      expiresIn: parsed.data.expires_in,
    };
  } catch (err) {
    if (err instanceof AssumeTenantError) {
      throw err;
    }
    if (err instanceof Error && err.name === 'AbortError') {
      logger.warn('assume_tenant_timeout', {
        requestId,
        timeoutMs: env.TOKEN_EXCHANGE_TIMEOUT_MS,
      });
      throw new AssumeTenantError(
        'unavailable',
        'TIMEOUT',
        'assume-tenant exchange timed out',
      );
    }
    // Network / DNS / unreachable — never expose the cause string (no token).
    logger.error('assume_tenant_error', { requestId });
    throw new AssumeTenantError(
      'unavailable',
      'NETWORK_ERROR',
      'assume-tenant exchange failed',
    );
  } finally {
    clearTimeout(timer);
  }
}

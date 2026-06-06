import { getServerEnv } from '@/shared/config/env';
import { getOperatorToken } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { RegistryUnavailableError, ApiError } from './errors';
import {
  RegistryResponseSchema,
  type RegistryResponse,
} from './registry-types';

/**
 * Server-side client for the GAP product/tenant registry.
 *
 * Path / auth / envelope per the authoritative producer contract
 * `console-registry-api.md` (TASK-BE-296):
 *   - `GET ${CONSOLE_REGISTRY_URL}` → `http://iam.local/api/admin/console/registry`
 *   - `Authorization: Bearer <operator-token>` — the **operator token
 *     obtained via the server-side RFC 8693 exchange**
 *     (`operator-token-exchange.ts`; console-integration-contract § 2.6 /
 *     ADR-MONO-014), held in its own HttpOnly cookie (`getOperatorToken()`).
 *     This is NOT the GAP OIDC access token: the GAP token is only ever the
 *     exchange `subject_token` and is never an `/api/admin/**` credential
 *     (§ 2.1 trust-boundary invariant — this closes the latent #569 defect).
 *     admin-service `OperatorAuthenticationFilter` verifies the token —
 *     gateway treats `/api/admin/**` as a public-path subtree and delegates.
 *   - No `X-Operator-Reason` (read-only catalog lookup).
 *   - The operator's tenant scope is resolved server-side from
 *     `admin_operators.tenant_id` — the console does NOT send a tenant; GAP
 *     returns only tenants the operator may select (multi-tenant isolation is
 *     enforced producer-side; M3/M4).
 *
 * Resilience (integration-heavy I1/I2 + console-integration-contract § 2.5):
 *   - Hard timeout (`REGISTRY_TIMEOUT_MS`) via AbortController.
 *   - 401/403 → caller forces re-login (no partial authed state).
 *   - timeout / 503 / network → `RegistryUnavailableError` so the caller can
 *     render a degraded catalog (never blank the shell).
 */
export async function fetchRegistry(): Promise<RegistryResponse> {
  const env = getServerEnv();
  const requestId = newRequestId();
  // The /api/admin/** credential is the EXCHANGED operator token — never the
  // GAP OIDC access token (§ 2.1/§ 2.2/§ 2.6). Absent operator token ⇒ no
  // usable operator session ⇒ 401 (caller re-logins; the exchange must run).
  const token = await getOperatorToken();

  if (!token) {
    throw new ApiError(401, 'TOKEN_INVALID', 'No operator session');
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), env.REGISTRY_TIMEOUT_MS);

  try {
    const res = await fetch(env.CONSOLE_REGISTRY_URL, {
      method: 'GET',
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${token}`,
        'X-Request-Id': requestId,
      },
      cache: 'no-store',
      signal: controller.signal,
    });

    if (res.status === 401 || res.status === 403) {
      const body = await res.json().catch(() => ({}));
      logger.warn('registry_unauthorized', {
        requestId,
        status: res.status,
        code: (body as { code?: string }).code,
      });
      throw new RegistryUnavailableError(
        'unauthorized',
        (body as { code?: string }).code ?? 'TOKEN_INVALID',
      );
    }

    if (res.status === 503) {
      const body = await res.json().catch(() => ({}));
      const code = (body as { code?: string }).code;
      logger.warn('registry_degraded', { requestId, status: 503, code });
      throw new RegistryUnavailableError(
        code === 'CIRCUIT_OPEN' ? 'circuit_open' : 'downstream',
        code ?? 'DOWNSTREAM_ERROR',
      );
    }

    if (!res.ok) {
      logger.warn('registry_unexpected_status', {
        requestId,
        status: res.status,
      });
      throw new RegistryUnavailableError(
        'downstream',
        `Registry returned ${res.status}`,
      );
    }

    const json = await res.json();
    const parsed = RegistryResponseSchema.parse(json);
    logger.info('registry_ok', {
      requestId,
      productCount: parsed.products.length,
    });
    return parsed;
  } catch (err) {
    if (err instanceof RegistryUnavailableError || err instanceof ApiError) {
      throw err;
    }
    if (err instanceof Error && err.name === 'AbortError') {
      logger.warn('registry_timeout', {
        requestId,
        timeoutMs: env.REGISTRY_TIMEOUT_MS,
      });
      throw new RegistryUnavailableError('timeout', 'Registry call timed out');
    }
    logger.error('registry_error', { requestId, err: String(err) });
    throw new RegistryUnavailableError('downstream', 'Registry call failed');
  } finally {
    clearTimeout(timer);
  }
}

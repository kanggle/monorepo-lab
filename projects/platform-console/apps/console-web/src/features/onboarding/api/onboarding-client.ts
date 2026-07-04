import { getServerEnv } from '@/shared/config/env';
import { logger, newRequestId } from '@/shared/lib/logger';
import { ApiError, OnboardingUnavailableError } from '@/shared/api/errors';
import {
  OnboardResponseSchema,
  type CreateOrganizationInput,
  type OnboardResponse,
} from './types';

/**
 * Server-only client for the self-service tenant-onboarding endpoint
 * (ADR-MONO-044 §3.4 / TASK-PC-FE-182 — `onboarding-api.md` § POST
 * /api/admin/onboarding/organizations).
 *
 * Authoritative producer contract (consume only, never redefine):
 *   - `iam/specs/contracts/http/onboarding-api.md` (request / 201 / errors).
 *   - Backing decision: ADR-MONO-044 (D1 atomic transaction, D2 new-tenant
 *     confinement, D3 fail-closed saga, D6 TENANT_ADMIN + TENANT_BILLING_ADMIN).
 *
 * Invariants (security-critical — the § 2.1 token boundary):
 *   - Server-only by construction: imported exclusively from the onboarding
 *     route handler (`runtime = 'nodejs'`); never reachable from client code.
 *   - The `subjectToken` (the caller's IAM OIDC access token) is ONLY the
 *     request input — never returned, NEVER logged. It is not an `/api/admin/**`
 *     credential; this ONE admin-service mutation accepts it in the body
 *     because the endpoint is `permitAll` and validates it itself.
 *
 * Error taxonomy (mirrors the operator-token-exchange resilience posture):
 *   - `401` → ApiError(401, TOKEN_INVALID) — subject token invalid/expired or
 *     rejected (operator/bootstrap token, no `sub`) → the caller re-logins.
 *   - `400` VALIDATION_ERROR → ApiError(400, …) — inline actionable.
 *   - `409` TENANT_ALREADY_EXISTS / OPERATOR_EMAIL_CONFLICT → ApiError(409, …)
 *     — inline actionable (nothing was created on a slug clash — D3 note).
 *   - `5xx` / timeout / network / unexpected shape → OnboardingUnavailableError
 *     — the onboarding form degrades to a retryable notice; the IAM login
 *     session is preserved (the visitor may retry).
 */
export async function createOrganization(
  input: CreateOrganizationInput,
  subjectToken: string,
): Promise<OnboardResponse> {
  const env = getServerEnv();
  const requestId = newRequestId();

  const controller = new AbortController();
  const timer = setTimeout(
    () => controller.abort(),
    env.ONBOARDING_TIMEOUT_MS,
  );

  try {
    const res = await fetch(env.CONSOLE_ONBOARDING_URL, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        'X-Request-Id': requestId,
      },
      // Verbatim per onboarding-api.md § Request (no extra fields). The
      // subjectToken rides in the body (ADR-014 token-exchange style —
      // admin-service has no user-JWT header-auth surface).
      body: JSON.stringify({
        subjectToken,
        tenantId: input.tenantId,
        organizationName: input.organizationName,
      }),
      cache: 'no-store',
      signal: controller.signal,
    });

    if (res.status === 401) {
      const body = (await res.json().catch(() => ({}))) as { code?: string };
      logger.warn('onboarding_unauthorized', { requestId });
      throw new ApiError(
        401,
        body.code ?? 'TOKEN_INVALID',
        'onboarding subject token rejected',
      );
    }

    if (res.status === 400 || res.status === 409) {
      const body = (await res.json().catch(() => ({}))) as {
        code?: string;
        message?: string;
      };
      logger.warn('onboarding_producer_error', {
        requestId,
        status: res.status,
        code: body.code,
      });
      throw new ApiError(
        res.status,
        body.code ?? (res.status === 409 ? 'CONFLICT' : 'VALIDATION_ERROR'),
        body.message ?? 'onboarding request rejected',
      );
    }

    if (!res.ok) {
      // 5xx DOWNSTREAM_ERROR / CIRCUIT_OPEN / anything else → unavailable.
      const body = (await res.json().catch(() => ({}))) as { code?: string };
      logger.warn('onboarding_unavailable', {
        requestId,
        status: res.status,
        code: body.code,
      });
      throw new OnboardingUnavailableError(
        'downstream',
        body.code ?? `HTTP_${res.status}`,
        `onboarding returned ${res.status}`,
      );
    }

    const parsed = OnboardResponseSchema.safeParse(await res.json());
    if (!parsed.success) {
      logger.warn('onboarding_bad_shape', { requestId });
      throw new OnboardingUnavailableError(
        'downstream',
        'BAD_RESPONSE_SHAPE',
        'onboarding returned an unexpected payload',
      );
    }

    logger.info('onboarding_ok', {
      requestId,
      tenantId: parsed.data.tenantId,
    });
    return parsed.data;
  } catch (err) {
    if (err instanceof ApiError || err instanceof OnboardingUnavailableError) {
      throw err;
    }
    if (err instanceof Error && err.name === 'AbortError') {
      logger.warn('onboarding_timeout', {
        requestId,
        timeoutMs: env.ONBOARDING_TIMEOUT_MS,
      });
      throw new OnboardingUnavailableError(
        'timeout',
        'TIMEOUT',
        'onboarding timed out',
      );
    }
    // Network / DNS / unreachable — never expose the cause (never a token).
    logger.error('onboarding_error', { requestId });
    throw new OnboardingUnavailableError(
      'downstream',
      'NETWORK_ERROR',
      'onboarding failed',
    );
  } finally {
    clearTimeout(timer);
  }
}

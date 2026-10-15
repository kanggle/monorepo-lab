import { getServerEnv } from '@/shared/config/env';
import { resolveBackendUrl } from '@/shared/config/demo-backend';
import { logger, newRequestId } from '@/shared/lib/logger';
import { ApiError, OnboardingUnavailableError } from '@/shared/api/errors';
import {
  OnboardResponseSchema,
  ReceivedInvitationsResponseSchema,
  type CreateOrganizationInput,
  type OnboardResponse,
  type ReceivedInvitationsResponse,
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
 *   - `409` TENANT_ALREADY_EXISTS / OPERATOR_EMAIL_CONFLICT /
 *     OPERATOR_ALREADY_PROVISIONED → ApiError(409, …) — inline actionable
 *     (nothing was created on a slug clash — D3 note).
 *   - `422` OPERATOR_OWNED_TENANT_LIMIT_EXCEEDED (TASK-MONO-784 S5/S6 —
 *     ADR-MONO-082 b OD-1, onboarding-api.md § 신원당 상한) → ApiError(422, …)
 *     — inline actionable, NOT a degrade: the caller already owns the
 *     configured max number of companies. Retrying never helps, so this must
 *     never be folded into {@link OnboardingUnavailableError} (that class'
 *     "retryable" framing — and its degrade-incident logging — would
 *     misrepresent a business-rule refusal as a service outage).
 *   - `403` `EMAIL_NOT_VERIFIED` / `ONBOARDING_ACCOUNT_NOT_ELIGIBLE` and the
 *     gateway's `429 RATE_LIMITED` (TASK-MONO-773 — the ADR-044 D4 trust gate,
 *     onboarding-api.md) → ApiError(status, code) — inline actionable, NOT a
 *     degrade: «retry later» never helps an unverified or ineligible account.
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

  // TASK-MONO-585 — the configured address is what the deployment was given;
  // this is the address to actually call. Off-demo it is byte-identical.
  const resolvedOnboardingUrl = await resolveBackendUrl(
    env.CONSOLE_ONBOARDING_URL,
  );

  const controller = new AbortController();
  const timer = setTimeout(
    () => controller.abort(),
    env.ONBOARDING_TIMEOUT_MS,
  );

  try {
    const res = await fetch(resolvedOnboardingUrl, {
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

    // TASK-MONO-773 S6 — 403 (trust gate) and 429 (gateway `onboarding` scope) are answers too.
    if (
      res.status === 400 ||
      res.status === 403 ||
      res.status === 409 ||
      res.status === 422 ||
      res.status === 429
    ) {
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
        body.code ?? defaultCodeFor(res.status),
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

/** The code an answer without a body code stands for (the producer always sends one — defensive). */
function defaultCodeFor(status: number): string {
  if (status === 409) return 'CONFLICT';
  if (status === 403) return 'PERMISSION_DENIED';
  if (status === 429) return 'RATE_LIMITED';
  return 'VALIDATION_ERROR';
}

/**
 * The IAM received-invitations endpoint, derived from the configured onboarding URL: a SIBLING path on the same
 * host (`…/onboarding/organizations` → `…/onboarding/received-invitations`). One configured address for the one
 * IAM onboarding surface — a second env var is a second thing a deployment can forget (the e2e compose forgot
 * the first one until TASK-MONO-773 S6).
 */
function receivedInvitationsUrl(onboardingUrl: string): string {
  return new URL('received-invitations', onboardingUrl).toString();
}

/**
 * TASK-MONO-773 S6 (OD-7) — «받은 초대»: the operator invitations addressed to the caller's VERIFIED email
 * (`onboarding-api.md` § POST /api/admin/onboarding/received-invitations). Server-only, the same `subjectToken`
 * flow as {@link createOrganization} — never an operator token. Read-only: the answer carries no token and no
 * link (acceptance stays the TASK-MONO-772 email-link flow).
 *
 * `200` (any `accountState`) → the parsed answer. `401` → ApiError(401) (re-login). Anything else — IAM `503`
 * (the email judgement could not be made), a timeout, a bad shape — → {@link OnboardingUnavailableError}: the
 * screen says «could not load», never «no invitations» («모른다» ≠ «없음»).
 */
export async function listReceivedInvitations(
  subjectToken: string,
): Promise<ReceivedInvitationsResponse> {
  const env = getServerEnv();
  const requestId = newRequestId();
  const url = receivedInvitationsUrl(await resolveBackendUrl(env.CONSOLE_ONBOARDING_URL));

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), env.ONBOARDING_TIMEOUT_MS);
  try {
    const res = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        'X-Request-Id': requestId,
      },
      body: JSON.stringify({ subjectToken }),
      cache: 'no-store',
      signal: controller.signal,
    });

    if (res.status === 401) {
      const body = (await res.json().catch(() => ({}))) as { code?: string };
      logger.warn('received_invitations_unauthorized', { requestId });
      throw new ApiError(401, body.code ?? 'TOKEN_INVALID', 'received-invitations subject token rejected');
    }
    if (!res.ok) {
      const body = (await res.json().catch(() => ({}))) as { code?: string };
      logger.warn('received_invitations_unavailable', { requestId, status: res.status, code: body.code });
      throw new OnboardingUnavailableError(
        'downstream',
        body.code ?? `HTTP_${res.status}`,
        `received-invitations returned ${res.status}`,
      );
    }
    const parsed = ReceivedInvitationsResponseSchema.safeParse(await res.json());
    if (!parsed.success) {
      logger.warn('received_invitations_bad_shape', { requestId });
      throw new OnboardingUnavailableError('downstream', 'BAD_RESPONSE_SHAPE', 'unexpected payload');
    }
    logger.info('received_invitations_ok', {
      requestId,
      accountState: parsed.data.accountState,
      count: parsed.data.items.length,
    });
    return parsed.data;
  } catch (err) {
    if (err instanceof ApiError || err instanceof OnboardingUnavailableError) throw err;
    if (err instanceof Error && err.name === 'AbortError') {
      logger.warn('received_invitations_timeout', { requestId, timeoutMs: env.ONBOARDING_TIMEOUT_MS });
      throw new OnboardingUnavailableError('timeout', 'TIMEOUT', 'received-invitations timed out');
    }
    // Network / DNS — never expose the cause (never a token).
    logger.error('received_invitations_error', { requestId });
    throw new OnboardingUnavailableError('downstream', 'NETWORK_ERROR', 'received-invitations failed');
  } finally {
    clearTimeout(timer);
  }
}

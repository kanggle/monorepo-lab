import { NextResponse } from 'next/server';
import { cookies } from 'next/headers';
import {
  getAccessToken,
  OPERATOR_COOKIE,
  tokenCookieOpts,
  clearNonOperatorMarker,
} from '@/shared/lib/session';
import { exchangeForOperatorToken } from '@/shared/lib/operator-token-exchange';
import { establishDefaultTenant } from '@/shared/lib/active-tenant-default';
import { createOrganization } from '@/features/onboarding/api/onboarding-client';
import { CreateOrganizationInputSchema } from '@/features/onboarding/api/types';
import { ApiError, OnboardingUnavailableError } from '@/shared/api/errors';
import { logger, newRequestId } from '@/shared/lib/logger';

export const runtime = 'nodejs';

/**
 * Same-origin self-service onboarding proxy (POST) — the «테넌트 생성» screen's
 * self mode (ADR-MONO-044 §3.4 / TASK-PC-FE-182; TASK-MONO-773 S6 — called by
 * the `/new-tenant` form for a non-operator's first company AND a company
 * operator's second company, console-integration-contract § 2.6.4). The browser
 * never touches admin-service directly (architecture.md § Forbidden Dependencies).
 *
 * Unlike every other `/api/**` proxy this is NOT operator-gated — it runs in
 * the non-operator state too (access token present, operator token absent). The
 * caller's IAM OIDC access token is read server-side and passed as the
 * onboarding `subjectToken` (its designed use; it is never an `/api/admin/**`
 * credential — § 2.1). On the producer 201 the endpoint has already provisioned
 * the tenant + the caller's first-admin operator (with `oidc_subject` = the
 * caller's account_id — TASK-BE-474-fix-001), so we immediately re-exchange the
 * SAME access token for an operator token (§ 2.6) and set the operator + active
 * tenant cookies — the visitor walks straight into the console as the new
 * tenant's admin (the ADR-044 D7 end-to-end proof).
 *
 * Resilience: if the re-exchange fails AFTER a 201 (transient), the tenant +
 * operator already exist durably — we return `{ ready: false }` (no error,
 * nothing to roll back) and the client sends the user to `/login`; a fresh
 * login now succeeds (they ARE an operator) → console. We never destroy the
 * session or surface a 5xx for a durable success.
 */
export async function POST(req: Request) {
  const requestId = newRequestId();

  const accessToken = await getAccessToken();
  if (!accessToken) {
    // No IAM login → not even a pre-operator. Force re-login.
    return NextResponse.json(
      { code: 'TOKEN_INVALID', message: 'no active session' },
      { status: 401 },
    );
  }

  const parsed = CreateOrganizationInputSchema.safeParse(
    await req.json().catch(() => null),
  );
  if (!parsed.success) {
    return NextResponse.json(
      {
        code: 'VALIDATION_ERROR',
        message:
          '조직 ID 는 소문자로 시작하는 2~32자 슬러그(영문 소문자·숫자·하이픈), 조직 이름은 1~100자여야 합니다.',
      },
      { status: 400 },
    );
  }

  let tenantId: string;
  try {
    const result = await createOrganization(parsed.data, accessToken);
    tenantId = result.tenantId;
  } catch (err) {
    if (err instanceof ApiError) {
      // 401 (re-login) / 400 VALIDATION_ERROR / 409 TENANT_ALREADY_EXISTS |
      // OPERATOR_EMAIL_CONFLICT | OPERATOR_ALREADY_PROVISIONED / 422
      // OPERATOR_OWNED_TENANT_LIMIT_EXCEEDED (TASK-MONO-784 S6) → inline
      // actionable passthrough.
      return NextResponse.json(
        { code: err.code, message: err.message },
        { status: err.status },
      );
    }
    if (err instanceof OnboardingUnavailableError) {
      logger.warn('onboarding_proxy_degraded', {
        requestId,
        reason: err.reason,
      });
      return NextResponse.json(
        { code: err.code, message: 'onboarding unavailable' },
        { status: 503 },
      );
    }
    logger.error('onboarding_proxy_error', { requestId });
    return NextResponse.json(
      { code: 'DOWNSTREAM_ERROR', message: 'onboarding unavailable' },
      { status: 503 },
    );
  }

  // Tenant + first-admin operator now exist (durable). Re-exchange the same IAM
  // access token for the operator token so the owner enters the console as the
  // new tenant's admin without a second login. Fail-soft: a re-exchange outage
  // does not undo the durable onboarding — the client re-logins instead.
  const jar = await cookies();
  try {
    const op = await exchangeForOperatorToken(accessToken);
    jar.set(OPERATOR_COOKIE, op.accessToken, {
      ...tokenCookieOpts,
      maxAge: op.expiresIn,
    });
    // TASK-MONO-773 S6 (§ 2.6.4 «Self-mode success») — the session is an
    // operator one now: the non-operator marker goes in the same response.
    clearNonOperatorMarker(jar);
    // …and the NEW tenant becomes the active one through the SAME default-assume
    // function the callback and the refresh use (§ 2.7), with the new tenant as
    // the preferred choice — tenant cookie + assumed token together, never the
    // bare tenant cookie this route used to set (that left the overview to a
    // re-assume hop). Never fatal: without it the owner is still in, unscoped.
    await establishDefaultTenant(jar, {
      accessToken,
      operatorToken: op.accessToken,
      requestId,
      via: 'onboarding',
      preferredTenant: tenantId,
    });
    logger.info('onboarding_ready', { requestId, tenantId });
    return NextResponse.json({ tenantId, ready: true }, { status: 201 });
  } catch (err) {
    // The org is created; only the immediate console-entry re-exchange failed.
    logger.warn('onboarding_reexchange_deferred', {
      requestId,
      tenantId,
      reason: err instanceof Error ? err.name : 'unknown',
    });
    return NextResponse.json({ tenantId, ready: false }, { status: 201 });
  }
}

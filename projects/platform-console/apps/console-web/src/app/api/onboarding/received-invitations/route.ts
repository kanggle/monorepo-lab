import { NextResponse } from 'next/server';
import { getAccessToken } from '@/shared/lib/session';
import { listReceivedInvitations } from '@/features/onboarding/api/onboarding-client';
import { ApiError, OnboardingUnavailableError } from '@/shared/api/errors';
import { logger, newRequestId } from '@/shared/lib/logger';

export const runtime = 'nodejs';

/**
 * Same-origin «받은 초대» BFF (TASK-MONO-773 S6 — console-integration-contract § 2.6.4 «Empty state» item 1;
 * IAM `onboarding-api.md` § POST /api/admin/onboarding/received-invitations, owner decision OD-7).
 *
 * Like the onboarding proxy next to it this is NOT operator-gated: it runs in the non-operator state. The
 * caller's IAM access token is read server-side and becomes the `subjectToken` (its designed use — never an
 * `/api/admin/**` credential, § 2.1). The answer passes through as-is: `accountState` + the invitations to the
 * caller's VERIFIED email — never a token, never a link (read-only: acceptance stays the TASK-MONO-772
 * email-link flow, owner decision 2026-10-15 UTC, 773 S1 Q6).
 *
 * The `/new-tenant` page reads the same data server-side through {@link listReceivedInvitations} directly; this
 * route is the contract's browser-facing surface for the same read.
 *
 *   - no access cookie → `401 TOKEN_INVALID`, no IAM call;
 *   - IAM `401` → `401` (re-login);
 *   - IAM `503` / timeout / bad shape → `503` — never an empty list («모른다» ≠ «없음»).
 */
export async function POST(_req: Request): Promise<NextResponse> {
  const requestId = newRequestId();
  const accessToken = await getAccessToken();
  if (!accessToken) {
    return NextResponse.json(
      { code: 'TOKEN_INVALID', message: 'no active session' },
      { status: 401 },
    );
  }

  try {
    const answer = await listReceivedInvitations(accessToken);
    return NextResponse.json(answer, { status: 200 });
  } catch (err) {
    if (err instanceof ApiError) {
      return NextResponse.json({ code: err.code, message: err.message }, { status: err.status });
    }
    if (err instanceof OnboardingUnavailableError) {
      logger.warn('received_invitations_proxy_degraded', { requestId, reason: err.reason });
      return NextResponse.json(
        { code: err.code, message: 'received invitations unavailable' },
        { status: 503 },
      );
    }
    logger.error('received_invitations_proxy_error', { requestId });
    return NextResponse.json(
      { code: 'DOWNSTREAM_ERROR', message: 'received invitations unavailable' },
      { status: 503 },
    );
  }
}

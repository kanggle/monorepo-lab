import {
  OperatorSummarySchema,
  type ChangePasswordInput,
  type UpdateProfileInput,
} from './types';
import { callGapOperators, OPERATORS_PREFIX } from './operators-client';

/**
 * operators api — self-service surface (TASK-PC-FE-110 split). The `/me/*`
 * endpoints the operator acts on for THEMSELVES (change own password / update
 * own profile) plus the self-id resolve for the OperatorsScreen UX gate. No
 * `operator.manage`, no `X-Operator-Reason`, no `Idempotency-Key` (self auth
 * flow per the producer). Re-exported verbatim through the `operators-api`
 * barrel. 0 behavior change.
 */

// ---------------------------------------------------------------------------
// 5. change-password — PATCH /api/admin/operators/me/password
//    SELF only (there is no admin-set-other-password endpoint). Valid
//    operator token only (no operator.manage, no X-Operator-Reason, no
//    Idempotency-Key per the producer). 204 No Content on success. The
//    current/new passwords are server-side only — NEVER logged/echoed.
// ---------------------------------------------------------------------------

export async function changeOwnPassword(
  input: ChangePasswordInput,
): Promise<void> {
  await callGapOperators(
    {
      method: 'PATCH',
      path: `${OPERATORS_PREFIX}/me/password`,
      // NO reason, NO idempotencyKey — self auth flow per the producer.
      body: {
        currentPassword: input.currentPassword,
        newPassword: input.newPassword,
      },
      expectNoContent: true,
    },
    () => undefined,
  );
}

// ---------------------------------------------------------------------------
// 6. update-profile — PATCH /api/admin/operators/me/profile (TASK-BE-306 /
//    TASK-PC-FE-016). SELF only (no admin-set-other-profile). Valid operator
//    token only — no X-Operator-Reason, no Idempotency-Key per the producer.
//    204 No Content on success. The value is the operator's chosen
//    finance-platform account UUID (opaque to IAM — TASK-BE-304 § Decision
//    authority); `null` clears the column. Body shape mirrors the read shape
//    on console-registry-api `operatorContext.defaultAccountId` verbatim.
// ---------------------------------------------------------------------------

export async function updateOwnProfile(
  input: UpdateProfileInput,
): Promise<void> {
  await callGapOperators(
    {
      method: 'PATCH',
      path: `${OPERATORS_PREFIX}/me/profile`,
      // NO reason, NO idempotencyKey — self auth flow per the producer.
      body: {
        operatorContext: { defaultAccountId: input.defaultAccountId },
      },
      expectNoContent: true,
    },
    () => undefined,
  );
}

// ---------------------------------------------------------------------------
// 8. getSelfOperatorIdOrNull — GET /api/admin/me
//    TASK-PC-FE-020 — server-side resolve the caller's own operatorId for the
//    `OperatorsScreen` self-row UX gate (PC-FE-017 honest gap (b) closure).
//    Fail-graceful: returns `null` on every observed failure mode (401, 403,
//    503/timeout/network, schema parse, unexpected). Producer
//    `400 SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH` is the authoritative
//    gate; this is the UX layer that hides the disallowed button.
//
//    Read-only / no mutation headers; uses the same hardened `callGapOperators`
//    site (operator token + active tenant + structured error logging).
// ---------------------------------------------------------------------------

export async function getSelfOperatorIdOrNull(): Promise<string | null> {
  try {
    const me = await callGapOperators(
      { method: 'GET', path: '/api/admin/me' },
      (json) => OperatorSummarySchema.parse(json),
    );
    return me.operatorId;
  } catch {
    // Every failure mode (ApiError 401/403/etc, OperatorsUnavailableError,
    // network, schema parse, unexpected) → null. The page renders with the
    // gate inactive; the next mutation surfaces the real error (e.g. list
    // call's 401 → redirect-to-login). The producer 400 on self-via-admin
    // remains the authoritative fail-safe — never a security regression.
    return null;
  }
}

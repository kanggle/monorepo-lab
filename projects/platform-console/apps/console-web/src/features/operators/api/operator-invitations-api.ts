import { getActiveTenant } from '@/shared/lib/session';
import { clampPageSize } from '@/shared/lib/pagination';
import { callGapOperators } from './operators-client';
import {
  OperatorInvitationSchema,
  OperatorInvitationPageSchema,
  type OperatorInvitation,
  type OperatorInvitationPage,
  type InviteOperatorInput,
  type InvitationListParams,
} from './invitation-types';

/**
 * operators api — operator INVITATIONS (TASK-MONO-772 S5; console contract
 * § 2.4.3 rows 11–14, producer `admin-api.md` § Operator Invitation).
 * Re-exported verbatim through the `operators-api` barrel.
 *
 * Every call rides the SAME hardened {@link callGapOperators} core as the
 * rest of the operators surface: exchanged operator token (never the IAM OIDC
 * token), active tenant in `X-Tenant-Id`, the operators timeout / degrade
 * taxonomy (`OperatorsUnavailableError` → only this section degrades) and the
 * `operators_*` log events (path + status only — the invitee email is in the
 * BODY, which is never logged).
 *
 * PER-ENDPOINT HEADER MATRIX (§ 2.4.3 — NOT uniform):
 *   - `POST /operator-invitations`              → `X-Operator-Reason` + `Idempotency-Key`
 *   - `GET  /operator-invitations`              → none (read)
 *   - `POST /operator-invitations/{id}:cancel`  → `X-Operator-Reason` ONLY (NO key)
 *   - `POST /operator-invitations/{id}:resend`  → `X-Operator-Reason` ONLY (NO key)
 *
 * 🔴 No function here returns, accepts or logs an invitation token / link —
 *    the producer never sends one and the parse strips unknown keys.
 */

export const INVITATIONS_PREFIX = '/api/admin/operator-invitations';

function invitationPath(invitationId: string, action: 'cancel' | 'resend'): string {
  // The producer's custom-method form `{id}:cancel` — the id is encoded, the
  // `:` separator is literal (Spring maps `/{invitationId}:cancel`).
  return `${INVITATIONS_PREFIX}/${encodeURIComponent(invitationId)}:${action}`;
}

// ---------------------------------------------------------------------------
// 11. invite — POST /api/admin/operator-invitations
//     HEADERS: X-Operator-Reason + Idempotency-Key (BOTH required).
//     201 even when the mail failed — `delivery.status = FAILED_*` (OD-4).
// ---------------------------------------------------------------------------

export async function createOperatorInvitation(
  input: InviteOperatorInput,
  reason: string,
  idempotencyKey: string,
): Promise<OperatorInvitation> {
  return callGapOperators(
    {
      method: 'POST',
      path: INVITATIONS_PREFIX,
      reason,
      idempotencyKey,
      body: {
        email: input.email,
        displayName: input.displayName,
        roles: input.roles,
        tenantId: input.tenantId,
      },
    },
    (json) => OperatorInvitationSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 12. list — GET /api/admin/operator-invitations (tenant-scoped; PENDING by
//     default). READ — no mutation headers. `tenantId` = the explicit param or
//     the ACTIVE tenant (same scoping rule as the operators list, MONO-175).
// ---------------------------------------------------------------------------

export async function listOperatorInvitations(
  params: InvitationListParams = {},
): Promise<OperatorInvitationPage> {
  const qs = new URLSearchParams();
  const tenant = params.tenantId ?? (await getActiveTenant());
  if (tenant) qs.set('tenantId', tenant);
  qs.set('status', params.status ?? 'PENDING');
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampPageSize(params.size, 20, 100)));
  return callGapOperators(
    { method: 'GET', path: `${INVITATIONS_PREFIX}?${qs.toString()}` },
    (json) => OperatorInvitationPageSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 13. cancel — POST /api/admin/operator-invitations/{id}:cancel
//     HEADERS: X-Operator-Reason ONLY — NO Idempotency-Key (a re-cancel of a
//     CANCELLED invitation is a 200 no-op producer-side).
// ---------------------------------------------------------------------------

export async function cancelOperatorInvitation(
  invitationId: string,
  reason: string,
): Promise<OperatorInvitation> {
  return callGapOperators(
    {
      method: 'POST',
      path: invitationPath(invitationId, 'cancel'),
      reason,
      // NO idempotencyKey — per the producer header matrix (§ 2.4.3).
    },
    (json) => OperatorInvitationSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 14. resend — POST /api/admin/operator-invitations/{id}:resend
//     HEADERS: X-Operator-Reason ONLY — NO Idempotency-Key (a retried resend
//     just rotates the token again). New token on the SAME invitation; the old
//     link dies. 200 even when the mail failed (`delivery.status = FAILED_*`).
// ---------------------------------------------------------------------------

export async function resendOperatorInvitation(
  invitationId: string,
  reason: string,
): Promise<OperatorInvitation> {
  return callGapOperators(
    {
      method: 'POST',
      path: invitationPath(invitationId, 'resend'),
      reason,
      // NO idempotencyKey — per the producer header matrix (§ 2.4.3).
    },
    (json) => OperatorInvitationSchema.parse(json),
  );
}

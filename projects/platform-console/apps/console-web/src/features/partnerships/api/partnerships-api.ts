import { clampPageSize } from '@/shared/lib/pagination';
import {
  PartnershipListSchema,
  type PartnershipList,
  type PartnershipListParams,
  PartnershipMutationResultSchema,
  type PartnershipMutationResult,
  ParticipantResultSchema,
  type ParticipantResult,
  type ScopeSet,
} from './types';
import { callPartnerships, PARTNERSHIPS_PREFIX } from './partnerships-client';

/**
 * Cross-org partnership operations (TASK-PC-FE-187 / ADR-MONO-045 §3.4,
 * admin-api.md § Partnership Management). Server-only — called from the
 * same-origin `/api/partnerships/**` proxy routes with the HttpOnly operator
 * token + active tenant (`X-Tenant-Id`) attached in {@link callPartnerships}.
 *
 * The tenant is ALWAYS the caller's active tenant (the acting-side host or
 * partner — the producer's D2 TenantScopeGuard confines every op to it). The
 * client NEVER supplies a tenant id; it is resolved server-side from the
 * active-tenant cookie, so a client can never target another tenant (neither in
 * the invite body nor in any path segment).
 *
 * NOTE the colon-verb mapping: the producer uses AIP-136 colon verbs
 * (`{id}:accept`); this app has no colon-verb route precedent, so the
 * same-origin proxy exposes REST segments (`/api/partnerships/{id}/accept`) and
 * only HERE — server-side — does the path map back to the producer colon verb.
 */

/** Fresh Idempotency-Key per invite attempt — `crypto.randomUUID()` with a
 *  defensive fallback (mirrors `shared/lib/logger` `newRequestId`), so a
 *  double-submit of the SAME confirmed invite is deduped producer-side. */
function newIdempotencyKey(): string {
  const g = globalThis as unknown as {
    crypto?: { randomUUID?: () => string };
  };
  return g.crypto?.randomUUID?.() ?? Math.random().toString(36).slice(2);
}

// ---------------------------------------------------------------------------
// list — GET /api/admin/partnerships (host-side + partner-side; READ)
//   No mutation headers (per the matrix). The active tenant confines the
//   result server-side (D2 read parity — 타 테넌트 파트너십 미노출).
// ---------------------------------------------------------------------------

export async function listPartnerships(
  params: PartnershipListParams = {},
): Promise<PartnershipList> {
  const qs = new URLSearchParams();
  if (params.role) qs.set('role', params.role);
  if (params.status) qs.set('status', params.status);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampPageSize(params.size, 20, 100)));
  return callPartnerships(
    { method: 'GET', path: `${PARTNERSHIPS_PREFIX}?${qs.toString()}` },
    (json) => PartnershipListSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// invite — POST /api/admin/partnerships
//   HEADERS: X-Operator-Reason + Idempotency-Key (BOTH). The host tenant is
//   the active tenant (X-Tenant-Id) — NEVER in the body. The idempotency key is
//   generated per invite so a double-submit of the SAME confirmed invite is
//   deduped producer-side.
// ---------------------------------------------------------------------------

export async function invitePartnership(
  partnerTenantId: string,
  delegatedScope: ScopeSet,
  reason: string,
): Promise<PartnershipMutationResult> {
  return callPartnerships(
    {
      method: 'POST',
      path: PARTNERSHIPS_PREFIX,
      reason,
      idempotencyKey: newIdempotencyKey(),
      body: { partnerTenantId, delegatedScope },
    },
    (json) => PartnershipMutationResultSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// lifecycle transitions — POST {id}:accept|:suspend|:reactivate|:terminate
//   HEADERS: X-Operator-Reason ONLY (NO Idempotency-Key — the producer
//   documents none; the state machine + status guard is the dedupe). No body.
//   `:accept` = partner only; `:suspend`/`:reactivate`/`:terminate` = either
//   party (the producer enforces the D2 side rule).
// ---------------------------------------------------------------------------

function transition(
  id: string,
  verb: 'accept' | 'suspend' | 'reactivate' | 'terminate',
  reason: string,
): Promise<PartnershipMutationResult> {
  return callPartnerships(
    {
      method: 'POST',
      // Producer colon verb (AIP-136) — the REST proxy segment maps here.
      path: `${PARTNERSHIPS_PREFIX}/${encodeURIComponent(id)}:${verb}`,
      reason,
      // NO idempotencyKey — per the producer header matrix.
    },
    (json) => PartnershipMutationResultSchema.parse(json),
  );
}

export function acceptPartnership(
  id: string,
  reason: string,
): Promise<PartnershipMutationResult> {
  return transition(id, 'accept', reason);
}

export function suspendPartnership(
  id: string,
  reason: string,
): Promise<PartnershipMutationResult> {
  return transition(id, 'suspend', reason);
}

export function reactivatePartnership(
  id: string,
  reason: string,
): Promise<PartnershipMutationResult> {
  return transition(id, 'reactivate', reason);
}

export function terminatePartnership(
  id: string,
  reason: string,
): Promise<PartnershipMutationResult> {
  return transition(id, 'terminate', reason);
}

// ---------------------------------------------------------------------------
// participant add — POST {id}/participants/{operatorId}
//   HEADERS: X-Operator-Reason ONLY (NO key). partner assigns own operator.
//   `participantScope` null ⇒ body omitted entirely (net-zero = full
//   delegatedScope); non-null ⇒ `{ participantScope }`.
// ---------------------------------------------------------------------------

export async function addParticipant(
  id: string,
  operatorId: string,
  participantScope: ScopeSet | null,
  reason: string,
): Promise<ParticipantResult> {
  return callPartnerships(
    {
      method: 'POST',
      path: `${PARTNERSHIPS_PREFIX}/${encodeURIComponent(
        id,
      )}/participants/${encodeURIComponent(operatorId)}`,
      reason,
      // Omit the body when scope is null so the producer applies the net-zero
      // default (full delegatedScope). A non-null scope must be ⊆ delegatedScope
      // (producer 422 PARTICIPANT_SCOPE_EXCEEDS_DELEGATION otherwise).
      body: participantScope === null ? undefined : { participantScope },
    },
    (json) => ParticipantResultSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// participant remove — DELETE {id}/participants/{operatorId}
//   HEADERS: X-Operator-Reason ONLY. 204 no content. partner offboards own
//   operator; the host-reach derivation dies on the next request (D6).
// ---------------------------------------------------------------------------

export async function removeParticipant(
  id: string,
  operatorId: string,
  reason: string,
): Promise<void> {
  await callPartnerships(
    {
      method: 'DELETE',
      path: `${PARTNERSHIPS_PREFIX}/${encodeURIComponent(
        id,
      )}/participants/${encodeURIComponent(operatorId)}`,
      reason,
      expectNoContent: true,
    },
    () => undefined,
  );
}

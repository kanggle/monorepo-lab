import { ErpUnavailableError } from '@/shared/api/errors';
import {
  callFlatEnvelopeGateway,
  type FlatEnvelopeGatewayProfile,
} from '@/shared/api/flat-envelope-gateway';
import {
  ApprovalDetailResponseSchema,
  type ApprovalRequest,
  type ApprovalListQueryParams,
  type ApprovalInboxQueryParams,
  APPROVAL_DEFAULT_PAGE_SIZE,
  APPROVAL_MAX_PAGE_SIZE,
} from './approval-types';

/**
 * Shared call core for the erp `approval-service` workflow client
 * (TASK-PC-FE-051 — ADR-MONO-016 § D3.1). As of TASK-PC-FE-243 the hardened
 * call site is the shared {@link callFlatEnvelopeGateway} FLAT-envelope core;
 * this file supplies the {@link APPROVAL_PROFILE} and holds the query-string
 * helpers + detail/mutation response parser — consumed by the `approval-reads`
 * + `approval-mutations` sub-modules, never imported by app code directly.
 * Behaviour is IDENTICAL to the pre-consolidation per-client copy.
 *
 * Server-only by construction (same posture as `erp-client.ts`). The token +
 * any data never reach client JS — client components call the same-origin
 * `/api/erp/approval/**` proxy routes.
 *
 * ── PER-DOMAIN CREDENTIAL — REUSE of § 2.4.8 ── the domain-facing IAM OIDC
 * token (`getDomainFacingToken()`), NEVER `getOperatorToken()`; NO
 * `X-Tenant-Id` (erp resolves tenant from the JWT claim). create + the 4
 * transitions carry a console-generated `Idempotency-Key`; the reasoned
 * transitions ALSO echo `X-Operator-Reason` (audit trail) — reads + submit +
 * a reasonless approve send none. The idempotency fail-fast guard is OFF (the
 * header is attached only when present).
 *
 * Error envelope: the flat erp shape `{ code, message, details?, timestamp }`.
 * The approval-specific codes (403 `APPROVAL_NOT_AUTHORIZED_APPROVER`, 409
 * `APPROVAL_STATUS_TRANSITION_INVALID` / `APPROVAL_ALREADY_FINALIZED`, 422
 * `APPROVAL_ROUTE_INVALID`, 404 `APPROVAL_REQUEST_NOT_FOUND`, `IDEMPOTENCY_*`)
 * surface as `ApiError` (inline actionable). Resilience (§ 2.5): 401 →
 * whole-session re-login; 403 → inline; 503 / timeout / network →
 * `ErpUnavailableError` (ONLY the approval section degrades). No rate-limit
 * handling (erp documents no 429 — the profile supplies no policy).
 *
 * Confidential / audit-heavy: structured logs are server-side only; the token,
 * the request title / subject / actor ids, and any reason text are NEVER logged
 * — the log `path` carries the sanitised `logPath` route shape.
 */

export interface CallOptions {
  /** Path relative to `${ERP_BASE_URL}` (includes any encoded `{id}` +
   *  the search params). */
  path: string;
  /** Sanitised path shape for logging (no record id — e.g.
   *  `/api/erp/approval/requests/{id}`). */
  logPath: string;
  method?: 'GET' | 'POST';
  body?: unknown;
  /** `Idempotency-Key` header — required on create + the 4 transitions. */
  idempotencyKey?: string;
  /** `X-Operator-Reason` header — set ONLY on the transitions that record a
   *  reason (the producer echoes it for the audit trail). Reads + create never
   *  set it. */
  operatorReason?: string;
}

/**
 * erp approval profile for the shared {@link callFlatEnvelopeGateway} core:
 * degrades via {@link ErpUnavailableError} and logs `erp_approval_*` events
 * against the erp `approval-service` at `${ERP_BASE_URL}` (timeout
 * `ERP_TIMEOUT_MS`). No rate-limit policy; no idempotency fail-fast guard.
 */
const APPROVAL_PROFILE: FlatEnvelopeGatewayProfile = {
  logPrefix: 'erp_approval',
  requestFailedLabel: 'erp approval request failed',
  resolveDefaults: (env) => ({
    baseUrl: env.ERP_BASE_URL,
    timeoutMs: env.ERP_TIMEOUT_MS,
  }),
  makeUnavailable: (reason, code, message) =>
    new ErpUnavailableError(reason, code, message),
  isUnavailable: (err) => err instanceof ErpUnavailableError,
  messages: {
    degraded: 'erp approval unavailable',
    timeout: 'erp approval call timed out',
    network: 'erp approval call failed',
  },
};

/**
 * Single hardened call site — a thin wrapper over the shared
 * {@link callFlatEnvelopeGateway} core with the {@link APPROVAL_PROFILE}.
 */
export async function callApproval<T>(
  opts: CallOptions,
  parse: (json: unknown) => T,
): Promise<T> {
  const { raw } = await callFlatEnvelopeGateway(
    {
      path: opts.path,
      logPath: opts.logPath,
      method: opts.method,
      body: opts.body,
      idempotencyKey: opts.idempotencyKey,
      operatorReason: opts.operatorReason,
    },
    parse,
    APPROVAL_PROFILE,
  );
  return raw;
}

// ---------------------------------------------------------------------------
// query-string helpers.
// ---------------------------------------------------------------------------

function pageParams(qs: URLSearchParams, page?: number, size?: number): void {
  qs.set('page', String(Math.max(0, page ?? 0)));
  qs.set(
    'size',
    String(
      Math.min(
        APPROVAL_MAX_PAGE_SIZE,
        Math.max(1, size ?? APPROVAL_DEFAULT_PAGE_SIZE),
      ),
    ),
  );
}

export function listQs(params: ApprovalListQueryParams): string {
  const qs = new URLSearchParams();
  if (params.status) qs.set('status', params.status);
  if (params.role) qs.set('role', params.role);
  pageParams(qs, params.page, params.size);
  return qs.toString();
}

export function inboxQs(params: ApprovalInboxQueryParams): string {
  const qs = new URLSearchParams();
  pageParams(qs, params.page, params.size);
  return qs.toString();
}

/** Parses a detail / mutation response envelope into the
 *  `ApprovalRequest` — tolerant `{ data, meta }` extraction. */
export function parseApprovalRequest(json: unknown): ApprovalRequest {
  const env = (json ?? {}) as { data?: unknown };
  return ApprovalDetailResponseSchema.parse({
    data: env.data,
    meta: (json as { meta?: unknown })?.meta ?? {},
  }).data;
}

import { ErpUnavailableError } from '@/shared/api/errors';
import {
  callFlatEnvelopeGateway,
  type FlatEnvelopeGatewayProfile,
} from '@/shared/api/flat-envelope-gateway';
import {
  DelegationListResponseSchema,
  type DelegationListResponse,
  type DelegationGrant,
  DelegationGrantSchema,
  type CreateDelegationInput,
} from './delegation-types';

/**
 * Server-side erp `approval-service` delegation grant client
 * (TASK-PC-FE-054 — PC-FE-053 follow-up; realises TASK-ERP-BE-013).
 * Consumes the §v2.1 AMENDMENT endpoints (base path `/api/erp/approval`):
 *
 *   read   listDelegations (GET /delegations ?role=DELEGATOR|DELEGATE)
 *   write  createDelegation (POST /delegations) + revokeDelegation (POST /{id}/revoke)
 *
 * As of TASK-PC-FE-243 the hardened call site is the shared
 * {@link callFlatEnvelopeGateway} FLAT-envelope core; this file supplies the
 * {@link DELEGATION_PROFILE}. Behaviour is IDENTICAL to the pre-consolidation
 * per-client copy.
 *
 * Server-only by construction (same posture as `approval-api.ts`). The token +
 * any data never reach client JS — client components call the same-origin
 * `/api/erp/approval/delegations/**` proxy routes.
 *
 * ── PER-DOMAIN CREDENTIAL — REUSE of § 2.4.8 ── the DOMAIN-FACING IAM OIDC
 * token (`getDomainFacingToken()`), NEVER `getOperatorToken()`. erp resolves the
 * tenant from the JWT `tenant_id` claim — the console sends NO `X-Tenant-Id`.
 * The delegator identity is the JWT `sub` — NOT in the request body.
 *
 * MUTATION discipline (producer §v2.1 delegation endpoints):
 *   - create + revoke each carry a console-generated `Idempotency-Key` (the
 *     fail-fast guard is OFF — the header is attached only when present).
 *   - The delegation `reason` rides in the request BODY (NOT the
 *     `X-Operator-Reason` header — the delegation endpoints define no
 *     operator-reason audit header, unlike the approval transitions).
 *
 * Error envelope: the flat erp shape `{ code, message, details?, timestamp }`.
 * The delegation-specific codes (422 `DELEGATION_INVALID`, 404
 * `DELEGATION_NOT_FOUND`, 403 `PERMISSION_DENIED`/`TENANT_FORBIDDEN`, 400
 * `VALIDATION_ERROR`/`IDEMPOTENCY_KEY_REQUIRED`, 409 `IDEMPOTENCY_KEY_CONFLICT`)
 * surface as `ApiError` (inline actionable). Resilience (§ 2.5): 401 → re-login;
 * 503 / timeout → `ErpUnavailableError` (ONLY the delegation section degrades).
 */

interface CallOptions {
  /** Path relative to `${ERP_BASE_URL}`. */
  path: string;
  /** Sanitised path shape for logging (no record id). */
  logPath: string;
  method?: 'GET' | 'POST';
  body?: unknown;
  /** `Idempotency-Key` header — required on create + revoke. */
  idempotencyKey?: string;
}

/**
 * erp delegation profile for the shared {@link callFlatEnvelopeGateway} core:
 * degrades via {@link ErpUnavailableError} and logs `erp_delegation_*` events
 * against the erp `approval-service` at `${ERP_BASE_URL}` (timeout
 * `ERP_TIMEOUT_MS`). No rate-limit policy; no idempotency fail-fast guard.
 */
const DELEGATION_PROFILE: FlatEnvelopeGatewayProfile = {
  logPrefix: 'erp_delegation',
  requestFailedLabel: 'erp delegation request failed',
  resolveDefaults: (env) => ({
    baseUrl: env.ERP_BASE_URL,
    timeoutMs: env.ERP_TIMEOUT_MS,
  }),
  makeUnavailable: (reason, code, message) =>
    new ErpUnavailableError(reason, code, message),
  isUnavailable: (err) => err instanceof ErpUnavailableError,
  messages: {
    degraded: 'erp delegation unavailable',
    timeout: 'erp delegation call timed out',
    network: 'erp delegation call failed',
  },
};

/**
 * Single hardened call site — a thin wrapper over the shared
 * {@link callFlatEnvelopeGateway} core with the {@link DELEGATION_PROFILE}.
 */
async function callDelegation<T>(
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
    },
    parse,
    DELEGATION_PROFILE,
  );
  return raw;
}

// ---------------------------------------------------------------------------
// reads.
// ---------------------------------------------------------------------------

/** `GET /api/erp/approval/delegations[?role=DELEGATOR|DELEGATE]` — list
 *  grants where the caller is the delegator (or delegate). */
export async function listDelegations(
  role?: 'DELEGATOR' | 'DELEGATE',
): Promise<DelegationListResponse> {
  const qs = role ? `?role=${encodeURIComponent(role)}` : '';
  return callDelegation(
    {
      path: `/api/erp/approval/delegations${qs}`,
      logPath: '/api/erp/approval/delegations',
    },
    (json) => DelegationListResponseSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// writes — create + revoke (each carries an Idempotency-Key).
// ---------------------------------------------------------------------------

/** `POST /api/erp/approval/delegations` — create a delegation grant.
 *  The delegator identity is the JWT `sub`; NOT sent in the body.
 *  Reason rides in the body (NOT X-Operator-Reason). */
export async function createDelegation(
  input: CreateDelegationInput,
  idempotencyKey: string,
): Promise<DelegationGrant> {
  return callDelegation(
    {
      path: '/api/erp/approval/delegations',
      logPath: '/api/erp/approval/delegations',
      method: 'POST',
      idempotencyKey,
      body: {
        delegateId: input.delegateId,
        validFrom: input.validFrom,
        ...(input.validTo ? { validTo: input.validTo } : {}),
        ...(input.reason ? { reason: input.reason } : {}),
      },
    },
    (json) => {
      // The producer returns the grant directly (201 body = grant).
      return DelegationGrantSchema.parse(json);
    },
  );
}

/** `POST /api/erp/approval/delegations/{id}/revoke` — revoke a delegation
 *  grant. Reason is REQUIRED and rides in the BODY. */
export async function revokeDelegation(
  id: string,
  reason: string,
  idempotencyKey: string,
): Promise<DelegationGrant> {
  return callDelegation(
    {
      path: `/api/erp/approval/delegations/${encodeURIComponent(id)}/revoke`,
      logPath: '/api/erp/approval/delegations/{id}/revoke',
      method: 'POST',
      idempotencyKey,
      body: { reason },
    },
    (json) => {
      return DelegationGrantSchema.parse(json);
    },
  );
}

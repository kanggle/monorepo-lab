import { getServerEnv } from '@/shared/config/env';
import { getOperatorToken, getActiveTenant } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { ApiError, AuditUnavailableError } from '@/shared/api/errors';
import {
  AuditPageSchema,
  type AuditPage,
  type AuditQueryParams,
  AUDIT_MAX_PAGE_SIZE,
  AUDIT_DEFAULT_PAGE_SIZE,
} from './types';

/**
 * Server-side GAP admin-service unified audit + security read client
 * (TASK-PC-FE-003 — ADR-MONO-013 Phase 2 slice 2).
 *
 * Server-only by construction (same posture as `accounts-api.ts` /
 * `registry-client.ts`): imported exclusively from server components and the
 * `runtime = 'nodejs'` route handler; `getServerEnv()` throws outside the
 * server runtime. The operator token + audit-row PII never reach client JS —
 * client components call the same-origin `/api/audit` proxy route, which
 * attaches the HttpOnly operator token here server-side.
 *
 * Auth invariant (console-integration-contract § 2.1/§ 2.4.2 — the #569
 * trust boundary): the call authenticates with the EXCHANGED operator token
 * (`getOperatorToken()`), NEVER the GAP OIDC access token. An absent
 * operator token ⇒ no usable operator session ⇒ `401 TOKEN_INVALID` (the
 * caller re-logins; the fetch is NOT made — no silent GAP-token fallback).
 *
 * Tenant invariant (§ 2.4 / multi-tenant): the operator's selected active
 * tenant is always sent as `X-Tenant-Id` (`getActiveTenant()`). When none
 * is selected the call is blocked with `400 NO_ACTIVE_TENANT` — never an
 * empty header (no cross-tenant leak). The `tenantId` *query* param — which
 * is what `AuditQueryUseCase` actually scopes the query by (admin-service does
 * NOT read `X-Tenant-Id` for audit) — **defaults to the active tenant**
 * (TASK-PC-FE-043) so the 감사·보안 view follows the tenant switcher. The
 * producer enforces the dual-read effective-scope gate (home ∪ assignments —
 * TASK-BE-249/BE-326), rejecting an out-of-scope tenant with 403
 * `TENANT_SCOPE_DENIED`. An explicit `params.tenantId` (SUPER_ADMIN
 * cross-tenant) overrides the default.
 *
 * READ-ONLY invariant (§ 2.4.2): this slice performs NO mutation. There is
 * NO `X-Operator-Reason` and NO `Idempotency-Key` on this call — carrying
 * over the § 2.4.1 mutation scaffolding would be a defect. The query is
 * meta-audited producer-side; the caller issues one call per user query
 * (no aggressive auto-refetch — hook-level concern).
 *
 * Resilience (§ 2.5 / integration-heavy I1): AbortController hard timeout;
 * 401 → `ApiError` (forced re-login); 403 PERMISSION_DENIED / 403
 * TENANT_SCOPE_DENIED / 422 VALIDATION_ERROR → `ApiError` (inline
 * actionable, no crash); 503/timeout → `AuditUnavailableError` (audit
 * section degrades only — shell intact).
 *
 * Logging: structured, server-side only; the operator token and audit-row
 * PII (account ids / masked IPs / geo) are NEVER logged (redacted) —
 * § 2.4.2 / § 2.6 logging invariant.
 */

const AUDIT_PATH = '/api/admin/audit';

/**
 * Builds the audit query string, applying the client-side guards that
 * pre-empt the producer `422 VALIDATION_ERROR` (task Edge Case / AC):
 *   - `size` is hard-capped to AUDIT_MAX_PAGE_SIZE (≤ 100);
 *   - `from > to` is rejected here (no fetch) with `422 AUDIT_RANGE_INVALID`.
 * Only the explicitly-supplied filters are serialised; `tenantId` is set when
 * present (the caller — `queryAudit` — defaults it to the active tenant, so it
 * is normally present; absent only if no active tenant, which is blocked upstream).
 */
function buildQuery(params: AuditQueryParams): string {
  const from = params.from?.trim();
  const to = params.to?.trim();
  if (from && to && from > to) {
    // ISO-8601 strings compare lexicographically iff well-formed; the
    // producer is the final authority, this is the cheap client guard.
    throw new ApiError(
      422,
      'AUDIT_RANGE_INVALID',
      'from must not be after to',
    );
  }

  const qs = new URLSearchParams();
  if (params.accountId && params.accountId.trim() !== '') {
    qs.set('accountId', params.accountId.trim());
  }
  if (params.actionCode && params.actionCode.trim() !== '') {
    qs.set('actionCode', params.actionCode.trim());
  }
  if (from) qs.set('from', from);
  if (to) qs.set('to', to);
  if (params.source) qs.set('source', params.source);
  // SUPER_ADMIN explicit cross-tenant ONLY — never fabricated here.
  if (params.tenantId && params.tenantId.trim() !== '') {
    qs.set('tenantId', params.tenantId.trim());
  }
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  const size = Math.min(
    AUDIT_MAX_PAGE_SIZE,
    Math.max(1, params.size ?? AUDIT_DEFAULT_PAGE_SIZE),
  );
  qs.set('size', String(size));
  return qs.toString();
}

/**
 * `GET /api/admin/audit` — the single unified-view read. Resolves the
 * operator token + active tenant, applies the timeout, and maps the
 * producer error envelope to the § 2.5 resilience taxonomy.
 */
export async function queryAudit(
  params: AuditQueryParams = {},
): Promise<AuditPage> {
  const env = getServerEnv();
  const requestId = newRequestId();

  // Trust boundary: the /api/admin/** credential is the EXCHANGED operator
  // token — never the GAP OIDC access token. Absent ⇒ 401, no fetch.
  const token = await getOperatorToken();
  if (!token) {
    logger.warn('audit_no_operator_session', { requestId });
    throw new ApiError(401, 'TOKEN_INVALID', 'No operator session');
  }

  // Multi-tenant: always send the selected tenant; block (no empty header)
  // when none is selected — never a cross-tenant / unscoped call.
  const tenant = await getActiveTenant();
  if (!tenant) {
    logger.warn('audit_no_active_tenant', { requestId });
    throw new ApiError(400, 'NO_ACTIVE_TENANT', 'No active tenant selected');
  }

  // Client guards (from ≤ to, size ≤ 100) — may throw 422 before any fetch.
  // TASK-PC-FE-043: default the audit query SCOPE to the active tenant so the
  // 감사·보안 view follows the tenant switcher (the producer scopes by the
  // `tenantId` query param, NOT `X-Tenant-Id`, and gates it against the
  // operator's dual-read effective scope). An explicit `params.tenantId`
  // (SUPER_ADMIN cross-tenant) overrides. `tenant` is non-null here (the
  // NO_ACTIVE_TENANT guard above already returned otherwise).
  const query = buildQuery({ ...params, tenantId: params.tenantId ?? tenant });

  const headers: Record<string, string> = {
    Accept: 'application/json',
    Authorization: `Bearer ${token}`,
    'X-Tenant-Id': tenant,
    'X-Request-Id': requestId,
  };
  // READ-ONLY: deliberately NO `X-Operator-Reason`, NO `Idempotency-Key`.

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), env.AUDIT_TIMEOUT_MS);

  try {
    const res = await fetch(`${env.IAM_ADMIN_API_BASE}${AUDIT_PATH}?${query}`, {
      method: 'GET',
      headers,
      cache: 'no-store',
      signal: controller.signal,
    });

    if (res.status === 401) {
      const errBody = (await res.json().catch(() => ({}))) as {
        code?: string;
      };
      logger.warn('audit_unauthorized', {
        requestId,
        status: 401,
        code: errBody.code,
      });
      // No partial authed state — caller forces a clean re-login.
      throw new ApiError(
        401,
        errBody.code ?? 'TOKEN_INVALID',
        'session expired',
      );
    }

    if (res.status === 403) {
      const errBody = (await res.json().catch(() => ({}))) as {
        code?: string;
        message?: string;
      };
      const code = errBody.code ?? 'PERMISSION_DENIED';
      logger.warn('audit_forbidden', { requestId, status: 403, code });
      // 403 PERMISSION_DENIED (incl. the intersection-permission rule:
      // a security source without `security.event.read`) and
      // 403 TENANT_SCOPE_DENIED → inline actionable (no crash, no re-login
      // loop — the operator simply lacks this view / tenant).
      throw new ApiError(403, code, errBody.message ?? 'not permitted');
    }

    if (res.status === 503) {
      const errBody = (await res.json().catch(() => ({}))) as {
        code?: string;
      };
      const code = errBody.code ?? 'DOWNSTREAM_ERROR';
      logger.warn('audit_degraded', { requestId, status: 503, code });
      // Audit section degrades only — shell stays intact (§ 2.5).
      throw new AuditUnavailableError(
        code === 'CIRCUIT_OPEN' ? 'circuit_open' : 'downstream',
        code,
        'GAP audit service unavailable',
      );
    }

    if (!res.ok) {
      // 422 VALIDATION_ERROR (from > to, size > 100 — should be pre-empted
      // by the client guard, but the producer is the final authority) →
      // inline field-level actionable (no crash).
      const errBody = (await res.json().catch(() => ({}))) as {
        code?: string;
        message?: string;
        timestamp?: string;
      };
      logger.warn('audit_request_error', {
        requestId,
        status: res.status,
        code: errBody.code,
      });
      throw new ApiError(
        res.status,
        errBody.code ?? `HTTP_${res.status}`,
        errBody.message ?? `audit request failed (${res.status})`,
        errBody.timestamp,
      );
    }

    const json = await res.json();
    // NB: the response is never logged — it carries producer-masked PII;
    // only a minimal non-PII summary is emitted.
    const parsed = AuditPageSchema.parse(json);
    logger.info('audit_ok', {
      requestId,
      status: res.status,
      rows: parsed.content.length,
      page: parsed.page,
    });
    return parsed;
  } catch (err) {
    if (err instanceof ApiError || err instanceof AuditUnavailableError) {
      throw err;
    }
    if (err instanceof Error && err.name === 'AbortError') {
      logger.warn('audit_timeout', {
        requestId,
        timeoutMs: env.AUDIT_TIMEOUT_MS,
      });
      throw new AuditUnavailableError(
        'timeout',
        'TIMEOUT',
        'GAP audit call timed out',
      );
    }
    logger.error('audit_error', { requestId });
    throw new AuditUnavailableError(
      'downstream',
      'NETWORK_ERROR',
      'GAP audit call failed',
    );
  } finally {
    clearTimeout(timer);
  }
}

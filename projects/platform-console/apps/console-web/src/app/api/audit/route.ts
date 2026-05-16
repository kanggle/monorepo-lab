import { NextResponse } from 'next/server';
import { queryAudit } from '@/features/audit/api/audit-api';
import type { AuditQueryParams, AuditSource } from '@/features/audit/api/types';
import { AUDIT_SOURCES } from '@/features/audit/api/types';
import { ApiError, AuditUnavailableError } from '@/shared/api/errors';
import { logger, newRequestId } from '@/shared/lib/logger';

export const runtime = 'nodejs';

/**
 * Same-origin audit read proxy for client components (the typed API
 * client's single backend entry point — no browser-direct GAP call,
 * architecture.md § Forbidden Dependencies / contract § 2.3). The HttpOnly
 * operator token + active tenant are attached server-side in `queryAudit()`.
 *
 * READ-ONLY (console-integration-contract § 2.4.2): GET only — there is NO
 * request body schema and NO mutation branch (the FE-002 `_proxy` mutation
 * mapping is deliberately NOT reused here; only its error→HTTP shape is
 * mirrored). The operator-token / reason / idempotency scaffolding of the
 * accounts mutation proxy does not apply.
 *
 *   - 401 → 401 (client api-client triggers refresh→re-login; no partial
 *     authed state).
 *   - 403 PERMISSION_DENIED (incl. the intersection-permission rule) /
 *     403 TENANT_SCOPE_DENIED → 403 (inline actionable, no crash).
 *   - 422 VALIDATION_ERROR → 422 (inline field-level, no crash).
 *   - 400 NO_ACTIVE_TENANT → 400 (tenant gate; never empty).
 *   - 503/timeout → 503 (the audit section degrades only; shell intact).
 *
 * No token / audit-row PII is ever logged.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const url = new URL(req.url);
  const sp = url.searchParams;

  const rawSource = sp.get('source') ?? undefined;
  const source: AuditSource | undefined =
    rawSource && (AUDIT_SOURCES as readonly string[]).includes(rawSource)
      ? (rawSource as AuditSource)
      : undefined;

  const params: AuditQueryParams = {
    accountId: sp.get('accountId') ?? undefined,
    actionCode: sp.get('actionCode') ?? undefined,
    from: sp.get('from') ?? undefined,
    to: sp.get('to') ?? undefined,
    source,
    tenantId: sp.get('tenantId') ?? undefined,
    page: sp.has('page') ? Number(sp.get('page')) : undefined,
    size: sp.has('size') ? Number(sp.get('size')) : undefined,
  };

  try {
    const result = await queryAudit(params);
    return NextResponse.json(result);
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      return NextResponse.json(
        { code: err.code, message: 'session expired' },
        { status: 401 },
      );
    }
    if (err instanceof ApiError && err.code === 'NO_ACTIVE_TENANT') {
      return NextResponse.json(
        { code: 'NO_ACTIVE_TENANT', message: 'no active tenant selected' },
        { status: 400 },
      );
    }
    if (err instanceof ApiError) {
      // 403 PERMISSION_DENIED / 403 TENANT_SCOPE_DENIED / 422
      // VALIDATION_ERROR → inline actionable (passthrough, no crash).
      return NextResponse.json(
        { code: err.code, message: err.message },
        { status: err.status },
      );
    }
    if (err instanceof AuditUnavailableError) {
      logger.warn('audit_proxy_degraded', { requestId, reason: err.reason });
      return NextResponse.json(
        { code: err.code, message: 'audit unavailable' },
        { status: 503 },
      );
    }
    logger.error('audit_proxy_error', { requestId });
    return NextResponse.json(
      { code: 'DOWNSTREAM_ERROR', message: 'audit unavailable' },
      { status: 503 },
    );
  }
}

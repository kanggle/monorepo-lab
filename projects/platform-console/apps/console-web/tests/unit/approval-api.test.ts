import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/erp-ops/api/approval-api.ts` — the erp `approval-service`
 * workflow client (TASK-PC-FE-051 — ADR-MONO-016 § D3.1 parity slice).
 *
 * Pins:
 *   - per-domain credential REUSE of § 2.4.8: the domain-facing GAP OIDC
 *     token, NEVER `getOperatorToken()`; no `X-Tenant-Id` (erp resolves
 *     tenant from the JWT claim);
 *   - create + the 4 transitions carry the console-generated
 *     `Idempotency-Key`; reads do not;
 *   - reject / withdraw / a reasoned approve echo `X-Operator-Reason`
 *     (the audit header) AND carry the reason in the body; submit + a
 *     reasonless approve send NO `X-Operator-Reason`;
 *   - NON_NULL absent-field parsing (reason / submittedAt / finalizedAt
 *     ABSENT → optional/undefined, never a parser throw);
 *   - the approval-specific error codes surface as `ApiError` (403
 *     not-authorized, 409 invalid-transition / finalized, 422
 *     route-invalid, idempotency) — inline actionable, no crash;
 *   - 503 / timeout → `ErpUnavailableError`.
 */

const cookieJar = new Map<string, string>();
vi.mock('next/headers', () => ({
  cookies: async () => ({
    get: (n: string) =>
      cookieJar.has(n) ? { value: cookieJar.get(n)! } : undefined,
  }),
}));

const { ENV } = vi.hoisted(() => ({
  ENV: {
    OIDC_ISSUER_URL: 'http://gap.local',
    OIDC_CLIENT_ID: 'platform-console-web',
    OIDC_REDIRECT_URI: 'http://console.local/api/auth/callback',
    OIDC_SCOPE: 'openid profile email tenant.read',
    CONSOLE_REGISTRY_URL: 'http://gap.local/api/admin/console/registry',
    REGISTRY_TIMEOUT_MS: 50,
    CONSOLE_TOKEN_EXCHANGE_URL:
      'http://gap.local/api/admin/auth/token-exchange',
    TOKEN_EXCHANGE_TIMEOUT_MS: 50,
    GAP_ADMIN_API_BASE: 'http://gap.local',
    ACCOUNTS_TIMEOUT_MS: 50,
    AUDIT_TIMEOUT_MS: 50,
    OPERATORS_TIMEOUT_MS: 50,
    WMS_ADMIN_BASE_URL: 'http://wms.local/api/v1/admin',
    WMS_TIMEOUT_MS: 50,
    SCM_GATEWAY_BASE_URL: 'http://scm.local',
    SCM_TIMEOUT_MS: 50,
    FINANCE_BASE_URL: 'http://finance.local',
    FINANCE_TIMEOUT_MS: 50,
    ERP_BASE_URL: 'http://erp.local',
    ERP_TIMEOUT_MS: 50,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import * as sessionModule from '@/shared/lib/session';
import {
  listApprovalRequests,
  getApprovalRequest,
  listApprovalInbox,
  createApprovalRequest,
  submitApproval,
  approveApproval,
  rejectApproval,
  withdrawApproval,
} from '@/features/erp-ops/api/approval-api';
import { ApprovalRequestSchema } from '@/features/erp-ops/api/approval-types';
import { ApiError, ErpUnavailableError } from '@/shared/api/errors';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function erpError(code: string, status: number) {
  return new Response(
    JSON.stringify({ code, message: 'e', timestamp: 't' }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

// A DRAFT detail body — reason present, submittedAt/finalizedAt ABSENT.
const DRAFT_DETAIL = {
  data: {
    id: 'appr-1',
    status: 'DRAFT',
    subjectType: 'DEPARTMENT',
    subjectId: 'dept-1',
    title: '조직개편 결재',
    approverId: 'emp-approver',
    submitterId: 'emp-submitter',
    reason: '사전 승인 요청',
    history: [],
    createdAt: '2026-06-05T00:00:00Z',
  },
  meta: { timestamp: 'x' },
};

// A SUBMITTED detail body — submittedAt present, finalizedAt ABSENT, no
// top-level reason (NON_NULL absence).
const SUBMITTED_DETAIL = {
  data: {
    id: 'appr-1',
    status: 'SUBMITTED',
    subjectType: 'DEPARTMENT',
    subjectId: 'dept-1',
    title: '조직개편 결재',
    approverId: 'emp-approver',
    submitterId: 'emp-submitter',
    history: [
      { transition: 'SUBMITTED', actor: 'emp-submitter', at: '2026-06-05T01:00:00Z' },
    ],
    createdAt: '2026-06-05T00:00:00Z',
    submittedAt: '2026-06-05T01:00:00Z',
  },
  meta: { timestamp: 'x' },
};

const LIST_ENVELOPE = {
  data: [
    {
      id: 'appr-1',
      status: 'SUBMITTED',
      subjectType: 'DEPARTMENT',
      subjectId: 'dept-1',
      title: '조직개편 결재',
      approverId: 'emp-approver',
      submitterId: 'emp-submitter',
      createdAt: '2026-06-05T00:00:00Z',
      submittedAt: '2026-06-05T01:00:00Z',
    },
    {
      id: 'appr-2',
      status: 'DRAFT',
      subjectType: 'EMPLOYEE',
      subjectId: 'emp-9',
      title: '직원 발령',
      approverId: 'emp-approver',
      submitterId: 'emp-submitter',
      createdAt: '2026-06-05T00:00:00Z',
      // submittedAt ABSENT (DRAFT) — NON_NULL.
    },
  ],
  meta: { page: 0, size: 20, totalElements: 2, timestamp: 'x' },
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function lastCall(fetchMock: ReturnType<typeof vi.fn>) {
  const [url, init] = fetchMock.mock.calls[fetchMock.mock.calls.length - 1];
  return {
    url: String(url),
    init: init as RequestInit,
    headers: (init as RequestInit).headers as Record<string, string>,
    body: JSON.parse(String((init as RequestInit).body ?? '{}')),
  };
}

// ===========================================================================
// credential + tenant-model.
// ===========================================================================

describe('approval-api — per-domain credential (REUSE of § 2.4.8)', () => {
  it('sends the domain-facing GAP token, NEVER the operator token / X-Tenant-Id', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const getDomainFacingSpy = vi.spyOn(sessionModule, 'getDomainFacingToken');
    const getOperatorSpy = vi.spyOn(sessionModule, 'getOperatorToken');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(LIST_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await listApprovalRequests({});

    const { headers, url } = lastCall(fetchMock);
    expect(headers.Authorization).toBe('Bearer GAP-OIDC-ACCESS');
    expect(headers.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(headers['X-Tenant-Id']).toBeUndefined();
    expect(url).toContain('http://erp.local/api/erp/approval/requests');
    expect(getDomainFacingSpy).toHaveBeenCalled();
    expect(getOperatorSpy).not.toHaveBeenCalled();
  });

  it('throws 401 with NO fetch when the GAP session is absent', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const err = await listApprovalRequests({}).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

// ===========================================================================
// reads — GET, no mutation artifacts; list/inbox/detail parsing.
// ===========================================================================

describe('approval-api — reads (GET; query forwarding; NON_NULL parsing)', () => {
  beforeEach(() => cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS'));

  it('list forwards status/role/page/size and is a pure GET (no idempotency / reason)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(LIST_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);
    await listApprovalRequests({ status: 'SUBMITTED', role: 'APPROVER', page: 1, size: 10 });
    const { init, headers, url } = lastCall(fetchMock);
    expect(init.method).toBe('GET');
    expect(init.body).toBeUndefined();
    expect(headers['Idempotency-Key']).toBeUndefined();
    expect(headers['X-Operator-Reason']).toBeUndefined();
    const u = new URL(url);
    expect(u.searchParams.get('status')).toBe('SUBMITTED');
    expect(u.searchParams.get('role')).toBe('APPROVER');
    expect(u.searchParams.get('page')).toBe('1');
    expect(u.searchParams.get('size')).toBe('10');
  });

  it('list parses a DRAFT summary whose submittedAt is ABSENT (NON_NULL → undefined)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(LIST_ENVELOPE)));
    const r = await listApprovalRequests({});
    const draft = r.data.find((x) => x.status === 'DRAFT');
    expect(draft).toBeDefined();
    expect(draft!.submittedAt).toBeUndefined();
  });

  it('inbox is a GET to /inbox forwarding page/size', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(LIST_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);
    await listApprovalInbox({ page: 2, size: 5 });
    const { init, url } = lastCall(fetchMock);
    expect(init.method).toBe('GET');
    const u = new URL(url);
    expect(u.pathname).toBe('/api/erp/approval/inbox');
    expect(u.searchParams.get('page')).toBe('2');
    expect(u.searchParams.get('size')).toBe('5');
  });

  it('detail parses history + the NON_NULL absent fields (DRAFT: submittedAt/finalizedAt undefined)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(DRAFT_DETAIL)));
    const d = await getApprovalRequest('appr-1');
    expect(d.status).toBe('DRAFT');
    expect(d.reason).toBe('사전 승인 요청');
    expect(d.submittedAt).toBeUndefined();
    expect(d.finalizedAt).toBeUndefined();
    expect(Array.isArray(d.history)).toBe(true);
    expect(d.history.length).toBe(0);
    // ApprovalRequestSchema parses the same body without throwing.
    expect(() => ApprovalRequestSchema.parse(DRAFT_DETAIL.data)).not.toThrow();
  });

  it('a SUBMITTED detail has submittedAt present but finalizedAt + top-level reason ABSENT', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(SUBMITTED_DETAIL)));
    const d = await getApprovalRequest('appr-1');
    expect(d.submittedAt).toBe('2026-06-05T01:00:00Z');
    expect(d.finalizedAt).toBeUndefined();
    expect(d.reason).toBeUndefined();
    expect(d.history[0].transition).toBe('SUBMITTED');
    // The SUBMITTED history entry carries no reason (NON_NULL).
    expect(d.history[0].reason).toBeUndefined();
  });

  it('v2.0: parses a detail with stages/currentStage/totalStages + history stage + actingForApproverId', async () => {
    const MULTI_DETAIL = {
      data: {
        id: 'appr-multi',
        status: 'IN_REVIEW',
        subjectType: 'DEPARTMENT',
        subjectId: 'dept-1',
        title: '다단계',
        approverId: 'emp-b',
        submitterId: 'emp-s',
        history: [
          {
            transition: 'SUBMITTED',
            actor: 'emp-s',
            at: '2026-06-05T01:00:00Z',
            stage: 0,
          },
          {
            transition: 'APPROVED',
            actor: 'emp-delegate',
            at: '2026-06-05T02:00:00Z',
            stage: 0,
            actingForApproverId: 'emp-a',
          },
        ],
        stages: [
          { stageIndex: 0, approverId: 'emp-a', status: 'APPROVED' },
          { stageIndex: 1, approverId: 'emp-b', status: 'PENDING' },
        ],
        currentStage: 1,
        totalStages: 2,
        createdAt: '2026-06-05T00:00:00Z',
        submittedAt: '2026-06-05T01:00:00Z',
      },
      meta: { timestamp: 'x' },
    };
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(MULTI_DETAIL)));
    const d = await getApprovalRequest('appr-multi');
    expect(d.status).toBe('IN_REVIEW');
    expect(d.stages).toHaveLength(2);
    expect(d.stages![0]).toMatchObject({ stageIndex: 0, approverId: 'emp-a', status: 'APPROVED' });
    expect(d.stages![1]).toMatchObject({ stageIndex: 1, approverId: 'emp-b', status: 'PENDING' });
    expect(d.currentStage).toBe(1);
    expect(d.totalStages).toBe(2);
    // History entry with stage + actingForApproverId.
    expect(d.history[0].stage).toBe(0);
    expect(d.history[0].actingForApproverId).toBeUndefined();
    expect(d.history[1].stage).toBe(0);
    expect(d.history[1].actingForApproverId).toBe('emp-a');
    // ApprovalRequestSchema parse does not throw.
    const { ApprovalRequestSchema } = await import('@/features/erp-ops/api/approval-types');
    expect(() => ApprovalRequestSchema.parse(MULTI_DETAIL.data)).not.toThrow();
  });

  it('v2.0: parses a detail WITHOUT stages/currentStage (legacy/absent) — no throw', async () => {
    // Identical to DRAFT_DETAIL which has no stages fields — already tested above.
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(DRAFT_DETAIL)));
    const d = await getApprovalRequest('appr-1');
    expect(d.stages).toBeUndefined();
    expect(d.currentStage).toBeUndefined();
    expect(d.totalStages).toBeUndefined();
    // No throw — optional fields absent.
    expect(() => d).not.toThrow();
  });
});

// ===========================================================================
// writes — create + transitions: method / Idempotency-Key / reason header.
// ===========================================================================

describe('approval-api — create + transitions (Idempotency-Key + X-Operator-Reason)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
  });

  it('create (legacy approverId) → POST /requests + Idempotency-Key + body; NO X-Operator-Reason', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(DRAFT_DETAIL, 201));
    vi.stubGlobal('fetch', fetchMock);
    await createApprovalRequest(
      { subjectType: 'DEPARTMENT', subjectId: 'dept-1', title: 'T', approverId: 'emp-a', reason: 'r' },
      'idem-create',
    );
    const { init, url, headers, body } = lastCall(fetchMock);
    expect(init.method).toBe('POST');
    expect(url).toBe('http://erp.local/api/erp/approval/requests');
    expect(headers['Idempotency-Key']).toBe('idem-create');
    expect(headers['Content-Type']).toBe('application/json');
    expect(headers['X-Operator-Reason']).toBeUndefined();
    // Legacy single-approver: sends approverId (NOT approverIds).
    expect(body).toMatchObject({
      subjectType: 'DEPARTMENT',
      subjectId: 'dept-1',
      title: 'T',
      approverId: 'emp-a',
      reason: 'r',
    });
    expect(body.approverIds).toBeUndefined();
  });

  it('create (multi-stage approverIds) → body contains approverIds, NOT approverId', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(DRAFT_DETAIL, 201));
    vi.stubGlobal('fetch', fetchMock);
    await createApprovalRequest(
      {
        subjectType: 'DEPARTMENT',
        subjectId: 'dept-1',
        title: 'Multi',
        approverIds: ['emp-a', 'emp-b'],
      },
      'idem-multi',
    );
    const { body, headers } = lastCall(fetchMock);
    expect(headers['Idempotency-Key']).toBe('idem-multi');
    // Multi-stage: sends approverIds (NOT approverId).
    expect(body.approverIds).toEqual(['emp-a', 'emp-b']);
    expect(body.approverId).toBeUndefined();
    expect(body.subjectType).toBe('DEPARTMENT');
  });

  it('submit → POST /{id}/submit + Idempotency-Key; empty body; NO X-Operator-Reason', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(SUBMITTED_DETAIL));
    vi.stubGlobal('fetch', fetchMock);
    await submitApproval('appr-1', 'idem-submit');
    const { init, url, headers, body } = lastCall(fetchMock);
    expect(init.method).toBe('POST');
    expect(url).toBe('http://erp.local/api/erp/approval/requests/appr-1/submit');
    expect(headers['Idempotency-Key']).toBe('idem-submit');
    expect(headers['X-Operator-Reason']).toBeUndefined();
    expect(body).toEqual({});
  });

  it('approve WITHOUT reason → POST /{id}/approve, NO X-Operator-Reason; WITH reason → header echoed + body', async () => {
    // no reason
    let fetchMock = vi.fn().mockResolvedValue(jsonResponse(SUBMITTED_DETAIL));
    vi.stubGlobal('fetch', fetchMock);
    await approveApproval('appr-1', 'idem-app');
    let c = lastCall(fetchMock);
    expect(c.url).toBe('http://erp.local/api/erp/approval/requests/appr-1/approve');
    expect(c.headers['X-Operator-Reason']).toBeUndefined();
    expect(c.body).toEqual({});

    // with reason
    fetchMock = vi.fn().mockResolvedValue(jsonResponse(SUBMITTED_DETAIL));
    vi.stubGlobal('fetch', fetchMock);
    await approveApproval('appr-1', 'idem-app2', '승인합니다');
    c = lastCall(fetchMock);
    expect(c.headers['X-Operator-Reason']).toBe('승인합니다');
    expect(c.body.reason).toBe('승인합니다');
  });

  it('reject → reason REQUIRED in body + X-Operator-Reason header', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(SUBMITTED_DETAIL));
    vi.stubGlobal('fetch', fetchMock);
    await rejectApproval('appr-1', '근거 부족', 'idem-rej');
    const { url, headers, body } = lastCall(fetchMock);
    expect(url).toBe('http://erp.local/api/erp/approval/requests/appr-1/reject');
    expect(headers['Idempotency-Key']).toBe('idem-rej');
    expect(headers['X-Operator-Reason']).toBe('근거 부족');
    expect(body.reason).toBe('근거 부족');
  });

  it('withdraw → reason REQUIRED in body + X-Operator-Reason header', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(SUBMITTED_DETAIL));
    vi.stubGlobal('fetch', fetchMock);
    await withdrawApproval('appr-1', '수정 필요', 'idem-wd');
    const { url, headers, body } = lastCall(fetchMock);
    expect(url).toBe('http://erp.local/api/erp/approval/requests/appr-1/withdraw');
    expect(headers['X-Operator-Reason']).toBe('수정 필요');
    expect(body.reason).toBe('수정 필요');
  });
});

// ===========================================================================
// error taxonomy — the approval-specific codes surface as ApiError.
// ===========================================================================

describe('approval-api — error taxonomy (graceful, no crash)', () => {
  beforeEach(() => cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS'));

  it('403 APPROVAL_NOT_AUTHORIZED_APPROVER → ApiError(403)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(erpError('APPROVAL_NOT_AUTHORIZED_APPROVER', 403)));
    const err = await approveApproval('appr-1', 'k').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
    expect(err.code).toBe('APPROVAL_NOT_AUTHORIZED_APPROVER');
  });

  it('409 APPROVAL_STATUS_TRANSITION_INVALID → ApiError(409)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(erpError('APPROVAL_STATUS_TRANSITION_INVALID', 409)));
    const err = await submitApproval('appr-1', 'k').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(409);
    expect(err.code).toBe('APPROVAL_STATUS_TRANSITION_INVALID');
  });

  it('409 APPROVAL_ALREADY_FINALIZED → ApiError(409)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(erpError('APPROVAL_ALREADY_FINALIZED', 409)));
    const err = await rejectApproval('appr-1', 'r', 'k').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.code).toBe('APPROVAL_ALREADY_FINALIZED');
  });

  it('422 APPROVAL_ROUTE_INVALID → ApiError(422)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(erpError('APPROVAL_ROUTE_INVALID', 422)));
    const err = await submitApproval('appr-1', 'k').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(422);
    expect(err.code).toBe('APPROVAL_ROUTE_INVALID');
  });

  it('404 APPROVAL_REQUEST_NOT_FOUND → ApiError(404)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(erpError('APPROVAL_REQUEST_NOT_FOUND', 404)));
    const err = await getApprovalRequest('nope').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
  });

  it('400 IDEMPOTENCY_KEY_REQUIRED → ApiError(400)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(erpError('IDEMPOTENCY_KEY_REQUIRED', 400)));
    const err = await createApprovalRequest(
      { subjectType: 'DEPARTMENT', subjectId: 'd', title: 't', approverId: 'a' },
      'k',
    ).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.code).toBe('IDEMPOTENCY_KEY_REQUIRED');
  });

  it('503 → ErpUnavailableError (approval section degrades only)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(erpError('SERVICE_UNAVAILABLE', 503)));
    const err = await listApprovalRequests({}).catch((e) => e);
    expect(err).toBeInstanceOf(ErpUnavailableError);
  });

  it('timeout → ErpUnavailableError(timeout)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((_u: string, init?: RequestInit) => {
        return new Promise((_res, rej) => {
          init?.signal?.addEventListener('abort', () => {
            const e = new Error('aborted');
            e.name = 'AbortError';
            rej(e);
          });
        });
      }),
    );
    const err = await listApprovalInbox({}).catch((e) => e);
    expect(err).toBeInstanceOf(ErpUnavailableError);
    expect(err.reason).toBe('timeout');
  });
});

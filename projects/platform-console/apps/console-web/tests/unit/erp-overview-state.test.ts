import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ApiError, ErpUnavailableError } from '@/shared/api/errors';

/**
 * TASK-PC-FE-232 — `getErpOverviewState` server fan-out for the standalone
 * `/erp` overview landing. PROMOTES + EXPANDS the former masters-embedded
 * TASK-PC-FE-161 `getErpMastersOverviewState`: the same 5 masterdata
 * `meta.totalElements` counts (`?page=0&size=1`, `asOf` E3 threading) PLUS
 * two new counts — 결재 대기 (`listApprovalInbox`) and 활성 위임
 * (`listDelegationFacts({status:'ACTIVE'})`). Covers: not-eligible
 * short-circuit, happy-path count mapping (all 7), `asOf` threading
 * (masterdata legs only — NOT approval/delegation), per-cell
 * degrade/forbidden INDEPENDENCE (a 503/403 in ANY ONE leg never blanks
 * the others — AC-2's decisive rule), and the whole-session 401 redirect.
 */

const { redirectMock } = vi.hoisted(() => ({ redirectMock: vi.fn() }));
vi.mock('next/navigation', () => ({
  redirect: (p: string) => {
    redirectMock(p);
    throw new Error(`REDIRECT:${p}`);
  },
}));

const m = vi.hoisted(() => ({
  listDepartments: vi.fn(),
  listEmployees: vi.fn(),
  listJobGrades: vi.fn(),
  listCostCenters: vi.fn(),
  listBusinessPartners: vi.fn(),
  listApprovalInbox: vi.fn(),
  listApprovalRequests: vi.fn(),
  listDelegationFacts: vi.fn(),
  getApprovalMetrics: vi.fn(),
}));
vi.mock('@/features/erp-ops/api/erp-api', () => ({
  listDepartments: m.listDepartments,
  listEmployees: m.listEmployees,
  listJobGrades: m.listJobGrades,
  listCostCenters: m.listCostCenters,
  listBusinessPartners: m.listBusinessPartners,
}));
vi.mock('@/features/erp-ops/api/approval-api', () => ({
  listApprovalInbox: m.listApprovalInbox,
  listApprovalRequests: m.listApprovalRequests,
  getApprovalMetrics: m.getApprovalMetrics,
}));
vi.mock('@/features/erp-ops/api/erp-delegation-facts-api', () => ({
  listDelegationFacts: m.listDelegationFacts,
}));

import { getErpOverviewState } from '@/features/erp-ops/api/overview-state';

/** erp `{ data: T[], meta: { totalElements } }` list envelope. */
const listResp = (totalElements: number, len = 0) => ({
  data: Array.from({ length: len }),
  meta: { totalElements },
});

/** `/api/erp/approval/metrics` body (TASK-MONO-809) — the shared contract shape. */
const metricsFixture = () => ({
  period: 'month',
  timezone: 'Asia/Seoul',
  current: { from: '2026-07-01T00:00:00+09:00', to: '2026-07-15T12:00:00+09:00' },
  previous: { from: '2026-06-01T00:00:00+09:00', to: '2026-06-15T12:00:00+09:00' },
  metrics: [
    { key: 'submitted', unit: 'count', currency: null, current: '12', previous: '9' },
    { key: 'approved', unit: 'count', currency: null, current: '8', previous: '8' },
    { key: 'rejected', unit: 'count', currency: null, current: '1', previous: '2' },
  ],
  series: [{ date: '2026-07-01', values: { submitted: '2', approved: '1', rejected: '0' } }],
});

function seedHappy() {
  m.getApprovalMetrics.mockResolvedValue(metricsFixture());
  m.listDepartments.mockResolvedValue(listResp(5));
  m.listEmployees.mockResolvedValue(listResp(120));
  m.listJobGrades.mockResolvedValue(listResp(8));
  m.listCostCenters.mockResolvedValue(listResp(14));
  m.listBusinessPartners.mockResolvedValue(listResp(37));
  m.listApprovalInbox.mockResolvedValue(listResp(3));
  m.listDelegationFacts.mockResolvedValue(listResp(2));
  // TASK-PC-FE-360 — per-status approval counts: DRAFT 0 · SUBMITTED 2 ·
  // IN_REVIEW 1 · APPROVED 4 · REJECTED 1 · WITHDRAWN 0.
  m.listApprovalRequests.mockImplementation((p: { status?: string } = {}) =>
    Promise.resolve(
      listResp(
        ({ SUBMITTED: 2, IN_REVIEW: 1, APPROVED: 4, REJECTED: 1 } as Record<
          string,
          number
        >)[p.status ?? ''] ?? 0,
      ),
    ),
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('getErpOverviewState (TASK-PC-FE-232)', () => {
  it('not eligible → no fan-out, notEligible flag, no erp call fabricated', async () => {
    const state = await getErpOverviewState(false);
    expect(state.notEligible).toBe(true);
    expect(state.counts).toHaveLength(0);
    expect(m.listDepartments).not.toHaveBeenCalled();
    expect(m.listApprovalInbox).not.toHaveBeenCalled();
    expect(m.listDelegationFacts).not.toHaveBeenCalled();
    expect(m.getApprovalMetrics).not.toHaveBeenCalled();
    expect(state.performance.status).toBe('degraded');
  });

  it('happy → maps all 7 counts from meta.totalElements', async () => {
    seedHappy();
    const state = await getErpOverviewState(true);

    expect(state.notEligible).toBe(false);
    const byKey = Object.fromEntries(state.counts.map((c) => [c.key, c]));
    expect(byKey.departments.count).toBe(5);
    expect(byKey.employees.count).toBe(120);
    expect(byKey.jobGrades.count).toBe(8);
    expect(byKey.costCenters.count).toBe(14);
    expect(byKey.businessPartners.count).toBe(37);
    expect(byKey.pendingApprovals.count).toBe(3);
    expect(byKey.activeDelegations.count).toBe(2);
    expect(state.counts.every((c) => c.status === 'ok')).toBe(true);

    // Counts derive from page=0,size=1 reads.
    expect(m.listDepartments).toHaveBeenCalledWith({ page: 0, size: 1 });
    expect(m.listApprovalInbox).toHaveBeenCalledWith({ page: 0, size: 1 });
    expect(m.listDelegationFacts).toHaveBeenCalledWith({
      status: 'ACTIVE',
      page: 0,
      size: 1,
    });
  });

  it('threads asOf (E3) through every MASTERDATA count leg verbatim — NOT the approval/delegation legs', async () => {
    seedHappy();
    await getErpOverviewState(true, '2026-01-01');
    expect(m.listEmployees).toHaveBeenCalledWith({
      page: 0,
      size: 1,
      asOf: '2026-01-01',
    });
    // Approval/delegation are current-time counts — no asOf threading.
    expect(m.listApprovalInbox).toHaveBeenCalledWith({ page: 0, size: 1 });
    expect(m.listDelegationFacts).toHaveBeenCalledWith({
      status: 'ACTIVE',
      page: 0,
      size: 1,
    });
  });

  it('falls back to page length when meta.totalElements is absent', async () => {
    seedHappy();
    m.listDepartments.mockResolvedValue({ data: [{}], meta: {} });
    const state = await getErpOverviewState(true);
    expect(state.counts.find((c) => c.key === 'departments')!.count).toBe(1);
  });

  it('INDEPENDENT degrade: approval-service 503 does NOT blank the masterdata counts (AC-2 decisive rule)', async () => {
    seedHappy();
    m.listApprovalInbox.mockRejectedValue(
      new ErpUnavailableError('downstream', 'SERVICE_UNAVAILABLE', 'down'),
    );
    const state = await getErpOverviewState(true);
    const approval = state.counts.find((c) => c.key === 'pendingApprovals')!;
    expect(approval.count).toBeNull();
    expect(approval.status).toBe('degraded');
    // Every other tile — INCLUDING delegation — is unaffected.
    expect(state.counts.find((c) => c.key === 'departments')!.status).toBe(
      'ok',
    );
    expect(
      state.counts.find((c) => c.key === 'activeDelegations')!.status,
    ).toBe('ok');
  });

  it('INDEPENDENT degrade: one masterdata count 503 does not blank approval/delegation or the other masterdata tiles', async () => {
    seedHappy();
    m.listBusinessPartners.mockRejectedValue(
      new ErpUnavailableError('downstream', 'SERVICE_UNAVAILABLE', 'down'),
    );
    const state = await getErpOverviewState(true);
    const bp = state.counts.find((c) => c.key === 'businessPartners')!;
    expect(bp.count).toBeNull();
    expect(bp.status).toBe('degraded');
    expect(state.counts.find((c) => c.key === 'departments')!.status).toBe(
      'ok',
    );
    expect(
      state.counts.find((c) => c.key === 'pendingApprovals')!.status,
    ).toBe('ok');
    expect(
      state.counts.find((c) => c.key === 'activeDelegations')!.status,
    ).toBe('ok');
  });

  it('per-cell forbidden: a 403 in the delegation leg → that count null/forbidden, others unaffected', async () => {
    seedHappy();
    m.listDelegationFacts.mockRejectedValue(
      new ApiError(403, 'FORBIDDEN', 'no'),
    );
    const state = await getErpOverviewState(true);
    const delegation = state.counts.find(
      (c) => c.key === 'activeDelegations',
    )!;
    expect(delegation.count).toBeNull();
    expect(delegation.status).toBe('forbidden');
    expect(
      state.counts.find((c) => c.key === 'pendingApprovals')!.status,
    ).toBe('ok');
  });

  it('401 in the approval leg → whole-session redirect(/login) (not a per-cell degrade)', async () => {
    seedHappy();
    m.listApprovalInbox.mockRejectedValue(
      new ApiError(401, 'TOKEN_INVALID', 'exp'),
    );
    await expect(getErpOverviewState(true)).rejects.toThrow(
      'REDIRECT:/login',
    );
    expect(redirectMock).toHaveBeenCalledWith('/login?error=session_expired');
  });

  // ── TASK-PC-FE-360 — approval-status distribution (6 independent cells) ───

  it('approval status: exactly 6 reads (size 1, one per status) + the inbox leg is NOT reused', async () => {
    seedHappy();
    const state = await getErpOverviewState(true);

    expect(m.listApprovalRequests).toHaveBeenCalledTimes(6);
    for (const status of [
      'DRAFT',
      'SUBMITTED',
      'IN_REVIEW',
      'APPROVED',
      'REJECTED',
      'WITHDRAWN',
    ]) {
      expect(m.listApprovalRequests).toHaveBeenCalledWith({
        status,
        page: 0,
        size: 1,
      });
    }
    // The 결재 대기 tile still comes from the inbox, one call.
    expect(m.listApprovalInbox).toHaveBeenCalledTimes(1);
    expect(
      state.approvalStatus.map((b) => [b.status, b.count, b.cellStatus]),
    ).toEqual([
      ['DRAFT', 0, 'ok'],
      ['SUBMITTED', 2, 'ok'],
      ['IN_REVIEW', 1, 'ok'],
      ['APPROVED', 4, 'ok'],
      ['REJECTED', 1, 'ok'],
      ['WITHDRAWN', 0, 'ok'],
    ]);
  });

  it('approval status: one 503 degrades ONLY that bucket — tiles + sibling buckets stay ok', async () => {
    seedHappy();
    m.listApprovalRequests.mockImplementation((p: { status?: string } = {}) =>
      p.status === 'IN_REVIEW'
        ? Promise.reject(
            new ErpUnavailableError('downstream', 'SERVICE_UNAVAILABLE', 'down'),
          )
        : Promise.resolve(listResp(3)),
    );
    const state = await getErpOverviewState(true);
    const byStatus = Object.fromEntries(state.approvalStatus.map((b) => [b.status, b]));
    expect(byStatus.IN_REVIEW.count).toBeNull();
    expect(byStatus.IN_REVIEW.cellStatus).toBe('degraded');
    expect(byStatus.APPROVED.count).toBe(3);
    expect(state.counts.every((c) => c.status === 'ok')).toBe(true);
  });

  it('approval status: a 403 bucket → forbidden', async () => {
    seedHappy();
    m.listApprovalRequests.mockRejectedValue(new ApiError(403, 'FORBIDDEN', 'no'));
    const state = await getErpOverviewState(true);
    expect(state.approvalStatus.every((b) => b.cellStatus === 'forbidden')).toBe(true);
    expect(state.counts.find((c) => c.key === 'pendingApprovals')!.status).toBe('ok');
  });

  it('approval status: a 401 in a bucket → whole-session redirect(/login)', async () => {
    seedHappy();
    m.listApprovalRequests.mockRejectedValue(new ApiError(401, 'TOKEN_INVALID', 'exp'));
    await expect(getErpOverviewState(true)).rejects.toThrow('REDIRECT:/login');
    expect(redirectMock).toHaveBeenCalledWith('/login?error=session_expired');
  });

  it('approval status: asOf does not reach the status reads; not eligible → none', async () => {
    seedHappy();
    await getErpOverviewState(true, '2026-01-01');
    expect(m.listApprovalRequests).toHaveBeenCalledWith({
      status: 'DRAFT',
      page: 0,
      size: 1,
    });
    vi.clearAllMocks();
    const off = await getErpOverviewState(false);
    expect(off.approvalStatus).toEqual([]);
    expect(m.listApprovalRequests).not.toHaveBeenCalled();
  });

  it('401 in any masterdata leg → whole-session redirect(/login) (not a per-cell degrade)', async () => {
    seedHappy();
    m.listCostCenters.mockRejectedValue(
      new ApiError(401, 'TOKEN_INVALID', 'exp'),
    );
    await expect(getErpOverviewState(true)).rejects.toThrow(
      'REDIRECT:/login',
    );
    expect(redirectMock).toHaveBeenCalledWith('/login?error=session_expired');
  });

  // ── TASK-MONO-809 — the «실적» strip ────────────────────────────────────

  it('performance: the period reaches the metrics leg (asOf is NOT passed)', async () => {
    seedHappy();
    const state = await getErpOverviewState(true, '2026-01-01', 'week');

    expect(m.getApprovalMetrics).toHaveBeenCalledTimes(1);
    expect(m.getApprovalMetrics.mock.calls[0]).toEqual(['week']);
    expect(state.period).toBe('week');
    expect(state.performance.status).toBe('ok');
    expect(state.performance.value?.metrics.map((x) => x.key)).toEqual([
      'submitted',
      'approved',
      'rejected',
    ]);
    // asOf still reaches the masterdata legs only.
    expect(m.listEmployees).toHaveBeenCalledWith({ page: 0, size: 1, asOf: '2026-01-01' });
    expect(m.listApprovalInbox).toHaveBeenCalledWith({ page: 0, size: 1 });
  });

  it('performance: default period is the month', async () => {
    seedHappy();
    const state = await getErpOverviewState(true);
    expect(m.getApprovalMetrics).toHaveBeenCalledWith('month');
    expect(state.period).toBe('month');
  });

  it('performance: a 503 degrades only the strip — the status block + counts stay ok', async () => {
    seedHappy();
    m.getApprovalMetrics.mockRejectedValue(
      new ErpUnavailableError('downstream', 'SERVICE_UNAVAILABLE', 'down'),
    );
    const state = await getErpOverviewState(true);
    expect(state.performance).toEqual({ status: 'degraded', value: null });
    expect(state.counts.every((c) => c.status === 'ok')).toBe(true);
    expect(state.approvalStatus.every((b) => b.cellStatus === 'ok')).toBe(true);
  });

  it('performance: a 400 or an unparseable body degrades the strip only', async () => {
    seedHappy();
    m.getApprovalMetrics.mockRejectedValue(new ApiError(400, 'VALIDATION_ERROR', 'period'));
    expect((await getErpOverviewState(true)).performance.status).toBe('degraded');

    m.getApprovalMetrics.mockResolvedValue({
      ...metricsFixture(),
      metrics: [{ key: 'submitted', unit: 'count', currency: null, current: 12, previous: '1' }],
    });
    const state = await getErpOverviewState(true);
    expect(state.performance.status).toBe('degraded');
    expect(state.counts.every((c) => c.status === 'ok')).toBe(true);
  });

  it('performance: a 403 -> forbidden; a 401 -> whole-session redirect', async () => {
    seedHappy();
    m.getApprovalMetrics.mockRejectedValue(new ApiError(403, 'FORBIDDEN', 'no'));
    const state = await getErpOverviewState(true);
    expect(state.performance.status).toBe('forbidden');
    expect(state.counts.every((c) => c.status === 'ok')).toBe(true);

    m.getApprovalMetrics.mockRejectedValue(new ApiError(401, 'TOKEN_INVALID', 'exp'));
    await expect(getErpOverviewState(true)).rejects.toThrow('REDIRECT:/login');
    expect(redirectMock).toHaveBeenCalledWith('/login?error=session_expired');
  });
});

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
  listDelegationFacts: vi.fn(),
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

function seedHappy() {
  m.listDepartments.mockResolvedValue(listResp(5));
  m.listEmployees.mockResolvedValue(listResp(120));
  m.listJobGrades.mockResolvedValue(listResp(8));
  m.listCostCenters.mockResolvedValue(listResp(14));
  m.listBusinessPartners.mockResolvedValue(listResp(37));
  m.listApprovalInbox.mockResolvedValue(listResp(3));
  m.listDelegationFacts.mockResolvedValue(listResp(2));
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
    expect(redirectMock).toHaveBeenCalledWith('/login');
  });

  it('401 in any masterdata leg → whole-session redirect(/login) (not a per-cell degrade)', async () => {
    seedHappy();
    m.listCostCenters.mockRejectedValue(
      new ApiError(401, 'TOKEN_INVALID', 'exp'),
    );
    await expect(getErpOverviewState(true)).rejects.toThrow(
      'REDIRECT:/login',
    );
    expect(redirectMock).toHaveBeenCalledWith('/login');
  });
});

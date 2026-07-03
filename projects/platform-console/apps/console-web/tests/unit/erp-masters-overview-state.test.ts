import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ApiError, ErpUnavailableError } from '@/shared/api/errors';

/**
 * TASK-PC-FE-161 — `getErpMastersOverviewState` server fan-out. Console-web
 * DIRECT fan-out reusing the existing erp master `list*` reads; each count
 * derives from `meta.totalElements` (`?page=0&size=1`; ADR-MONO-017 D3.B — no
 * producer `/summary`). Covers: not-eligible short-circuit, count mapping,
 * `asOf` (E3) threading, per-cell degrade/forbidden, and the whole-session 401
 * redirect.
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
}));
vi.mock('@/features/erp-ops/api/erp-api', () => ({
  listDepartments: m.listDepartments,
  listEmployees: m.listEmployees,
  listJobGrades: m.listJobGrades,
  listCostCenters: m.listCostCenters,
  listBusinessPartners: m.listBusinessPartners,
}));

import { getErpMastersOverviewState } from '@/features/erp-ops/api/overview-state';

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
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('getErpMastersOverviewState (TASK-PC-FE-161)', () => {
  it('not eligible → no fan-out, notEligible flag, no erp call fabricated', async () => {
    const state = await getErpMastersOverviewState(false);
    expect(state.notEligible).toBe(true);
    expect(state.counts).toHaveLength(0);
    expect(m.listDepartments).not.toHaveBeenCalled();
    expect(m.listEmployees).not.toHaveBeenCalled();
  });

  it('happy → maps the 5 master counts from meta.totalElements', async () => {
    seedHappy();
    const state = await getErpMastersOverviewState(true);

    expect(state.notEligible).toBe(false);
    const byKey = Object.fromEntries(state.counts.map((c) => [c.key, c]));
    expect(byKey.departments.count).toBe(5);
    expect(byKey.employees.count).toBe(120);
    expect(byKey.jobGrades.count).toBe(8);
    expect(byKey.costCenters.count).toBe(14);
    expect(byKey.businessPartners.count).toBe(37);
    expect(byKey.departments.status).toBe('ok');

    // Count derives from a page=0,size=1 read.
    expect(m.listDepartments).toHaveBeenCalledWith({ page: 0, size: 1 });
  });

  it('threads asOf (E3) through every count leg verbatim', async () => {
    seedHappy();
    await getErpMastersOverviewState(true, '2026-01-01');
    expect(m.listEmployees).toHaveBeenCalledWith({
      page: 0,
      size: 1,
      asOf: '2026-01-01',
    });
  });

  it('falls back to page length when meta.totalElements is absent', async () => {
    seedHappy();
    m.listDepartments.mockResolvedValue({ data: [{}], meta: {} });
    const state = await getErpMastersOverviewState(true);
    expect(state.counts.find((c) => c.key === 'departments')!.count).toBe(1);
  });

  it('per-cell degrade: a 503 leg → that count null/degraded, others unaffected', async () => {
    seedHappy();
    m.listEmployees.mockRejectedValue(
      new ErpUnavailableError('downstream', 'SERVICE_UNAVAILABLE', 'down'),
    );
    const state = await getErpMastersOverviewState(true);
    const emp = state.counts.find((c) => c.key === 'employees')!;
    expect(emp.count).toBeNull();
    expect(emp.status).toBe('degraded');
    expect(state.counts.find((c) => c.key === 'departments')!.status).toBe('ok');
  });

  it('per-cell forbidden: a 403 leg → that count null/forbidden', async () => {
    seedHappy();
    m.listJobGrades.mockRejectedValue(new ApiError(403, 'FORBIDDEN', 'no'));
    const state = await getErpMastersOverviewState(true);
    const jg = state.counts.find((c) => c.key === 'jobGrades')!;
    expect(jg.count).toBeNull();
    expect(jg.status).toBe('forbidden');
  });

  it('401 in any leg → whole-session redirect(/login) (not a per-cell degrade)', async () => {
    seedHappy();
    m.listCostCenters.mockRejectedValue(
      new ApiError(401, 'TOKEN_INVALID', 'exp'),
    );
    await expect(getErpMastersOverviewState(true)).rejects.toThrow(
      'REDIRECT:/login',
    );
    expect(redirectMock).toHaveBeenCalledWith('/login');
  });
});

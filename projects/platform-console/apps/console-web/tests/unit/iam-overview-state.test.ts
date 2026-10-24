import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  ApiError,
  OperatorsUnavailableError,
  AccountsUnavailableError,
  AuditUnavailableError,
} from '@/shared/api/errors';

/**
 * TASK-PC-FE-180 — `getIamOverviewState` server fan-out (the live `/iam`
 * overview that splits from the static guide). Covers: no-active-tenant
 * page-level gate short-circuit, count mapping (operators total + ACTIVE/
 * SUSPENDED split, accounts total, audit total + recent), per-cell
 * degrade/forbidden across the three distinct IAM error taxonomies, and the
 * whole-session 401 redirect.
 */

const { redirectMock } = vi.hoisted(() => ({ redirectMock: vi.fn() }));
vi.mock('next/navigation', () => ({
  redirect: (p: string) => {
    redirectMock(p);
    throw new Error(`REDIRECT:${p}`);
  },
}));

const m = vi.hoisted(() => ({
  getActiveTenant: vi.fn(),
  listOperators: vi.fn(),
  searchAccounts: vi.fn(),
  queryAudit: vi.fn(),
  getIamMetrics: vi.fn(),
}));
vi.mock('@/shared/lib/session', () => ({ getActiveTenant: m.getActiveTenant }));
vi.mock('@/shared/api/iam-operators-read', () => ({
  listOperators: m.listOperators,
}));
vi.mock('@/shared/api/iam-accounts-read', () => ({
  searchAccounts: m.searchAccounts,
}));
vi.mock('@/shared/api/iam-audit-read', () => ({ queryAudit: m.queryAudit }));
vi.mock('@/features/iam-overview/api/iam-metrics-api', () => ({
  getIamMetrics: m.getIamMetrics,
}));

import { getIamOverviewState } from '@/features/iam-overview/api/overview-state';

const page = (totalElements: number, content: unknown[] = []) => ({
  content,
  page: 0,
  size: content.length || 1,
  totalElements,
  totalPages: 1,
});

const adminRow = (auditId: string) => ({
  source: 'admin',
  auditId,
  actionCode: 'ACCOUNT_LOCK',
  operatorId: 'op1',
  outcome: 'SUCCESS',
  occurredAt: '2026-07-01T00:00:00Z',
});

/** Default happy fan-out: tenant selected, every leg resolves. */
function seedHappy() {
  m.getActiveTenant.mockResolvedValue('wms');
  m.listOperators.mockImplementation((p: { status?: string } = {}) => {
    if (p.status === 'ACTIVE') return Promise.resolve(page(8));
    if (p.status === 'SUSPENDED') return Promise.resolve(page(2));
    return Promise.resolve(page(10));
  });
  // TASK-PC-FE-181: total (no status) vs LOCKED sub-leg differentiated by param.
  m.searchAccounts.mockImplementation((p: { status?: string } = {}) =>
    Promise.resolve(p.status === 'LOCKED' ? page(4) : page(123)),
  );
  m.queryAudit.mockResolvedValue(page(57, [adminRow('a1'), adminRow('a2')]));
  m.getIamMetrics.mockResolvedValue(METRICS);
}

/** `GET /api/admin/metrics` (TASK-MONO-811) — the bare shared shape. */
const METRICS = {
  period: 'month',
  timezone: 'Asia/Seoul',
  current: { from: '2026-10-01T00:00:00+09:00', to: '2026-10-24T14:05:00+09:00' },
  previous: { from: '2026-09-01T00:00:00+09:00', to: '2026-09-24T14:05:00+09:00' },
  metrics: [
    { key: 'loginSuccess', unit: 'count', currency: null, current: '412', previous: '398' },
    { key: 'loginFailure', unit: 'count', currency: null, current: '37', previous: '41' },
    { key: 'accountLocked', unit: 'count', currency: null, current: '3', previous: '5' },
    { key: 'newAccount', unit: 'count', currency: null, current: '18', previous: '12' },
  ],
  series: [
    {
      date: '2026-10-01',
      values: { loginSuccess: '20', loginFailure: '1', accountLocked: '0', newAccount: '1' },
    },
  ],
};

beforeEach(() => {
  vi.clearAllMocks();
});

describe('getIamOverviewState (TASK-PC-FE-180)', () => {
  it('no active tenant → page-level gate, no fan-out', async () => {
    m.getActiveTenant.mockResolvedValue(null);
    const state = await getIamOverviewState();
    expect(state.noActiveTenant).toBe(true);
    expect(m.listOperators).not.toHaveBeenCalled();
    expect(m.searchAccounts).not.toHaveBeenCalled();
    expect(m.queryAudit).not.toHaveBeenCalled();
  });

  it('happy → maps operator total + ACTIVE/SUSPENDED split, account total, audit total + recent', async () => {
    seedHappy();
    const state = await getIamOverviewState();

    expect(state.noActiveTenant).toBe(false);
    expect(state.operators.status).toBe('ok');
    expect(state.operators.total).toBe(10);
    expect(state.operators.active).toBe(8);
    expect(state.operators.suspended).toBe(2);

    expect(state.accounts.status).toBe('ok');
    expect(state.accounts.total).toBe(123);
    expect(state.accounts.locked).toBe(4);

    expect(state.audit.status).toBe('ok');
    expect(state.audit.total).toBe(57);
    expect(state.audit.recent).toHaveLength(2);
  });

  it('zero counts render ok (not degraded)', async () => {
    seedHappy();
    m.listOperators.mockResolvedValue(page(0));
    m.searchAccounts.mockResolvedValue(page(0));
    m.queryAudit.mockResolvedValue(page(0, []));
    const state = await getIamOverviewState();
    expect(state.operators.status).toBe('ok');
    expect(state.operators.total).toBe(0);
    expect(state.accounts.total).toBe(0);
    expect(state.audit.total).toBe(0);
    expect(state.audit.recent).toEqual([]);
  });

  it('per-cell forbidden: operators 403 → operators forbidden, accounts/audit unaffected', async () => {
    seedHappy();
    m.listOperators.mockImplementation((p: { status?: string } = {}) => {
      if (p.status) return Promise.resolve(page(0));
      return Promise.reject(new ApiError(403, 'PERMISSION_DENIED', 'no'));
    });
    const state = await getIamOverviewState();
    expect(state.operators.status).toBe('forbidden');
    expect(state.operators.total).toBeNull();
    expect(state.accounts.status).toBe('ok');
    expect(state.audit.status).toBe('ok');
  });

  it('per-cell degrade: accounts 503 (AccountsUnavailableError) → accounts degraded, others ok', async () => {
    seedHappy();
    m.searchAccounts.mockRejectedValue(
      new AccountsUnavailableError('downstream', 'DOWNSTREAM_ERROR', 'down'),
    );
    const state = await getIamOverviewState();
    expect(state.accounts.status).toBe('degraded');
    expect(state.accounts.total).toBeNull();
    expect(state.operators.status).toBe('ok');
    expect(state.audit.status).toBe('ok');
  });

  it('audit 503 (AuditUnavailableError) → audit degraded, recent null', async () => {
    seedHappy();
    m.queryAudit.mockRejectedValue(
      new AuditUnavailableError('timeout', 'TIMEOUT', 'slow'),
    );
    const state = await getIamOverviewState();
    expect(state.audit.status).toBe('degraded');
    expect(state.audit.total).toBeNull();
    expect(state.audit.recent).toBeNull();
    expect(state.operators.status).toBe('ok');
  });

  it('TASK-PC-FE-181: accounts total ok but LOCKED sub-leg degraded → total shown, locked null', async () => {
    seedHappy();
    m.searchAccounts.mockImplementation((p: { status?: string } = {}) => {
      if (p.status === 'LOCKED')
        return Promise.reject(
          new AccountsUnavailableError('downstream', 'DOWNSTREAM_ERROR', 'x'),
        );
      return Promise.resolve(page(123));
    });
    const state = await getIamOverviewState();
    expect(state.accounts.status).toBe('ok');
    expect(state.accounts.total).toBe(123);
    expect(state.accounts.locked).toBeNull();
  });

  it('operators total ok but a split leg degraded → total shown, split null', async () => {
    seedHappy();
    m.listOperators.mockImplementation((p: { status?: string } = {}) => {
      if (p.status === 'ACTIVE')
        return Promise.reject(
          new OperatorsUnavailableError('downstream', 'DOWNSTREAM_ERROR', 'x'),
        );
      if (p.status === 'SUSPENDED') return Promise.resolve(page(2));
      return Promise.resolve(page(10));
    });
    const state = await getIamOverviewState();
    expect(state.operators.status).toBe('ok');
    expect(state.operators.total).toBe(10);
    expect(state.operators.active).toBeNull();
    expect(state.operators.suspended).toBe(2);
  });

  it('401 in any leg → whole-session redirect(/login) (not a per-cell degrade)', async () => {
    seedHappy();
    m.queryAudit.mockRejectedValue(new ApiError(401, 'TOKEN_INVALID', 'exp'));
    await expect(getIamOverviewState()).rejects.toThrow('REDIRECT:/login');
    expect(redirectMock).toHaveBeenCalledWith('/login?error=session_expired');
  });
});

describe('getIamOverviewState — «실적» strip (TASK-MONO-811)', () => {
  it('asks for the metrics with the period and the ACTIVE tenant (the tenantId the producer scopes by)', async () => {
    seedHappy();
    const state = await getIamOverviewState('week');
    expect(m.getIamMetrics).toHaveBeenCalledTimes(1);
    expect(m.getIamMetrics).toHaveBeenCalledWith('week', 'wms');
    expect(state.period).toBe('week');
    expect(state.performance.status).toBe('ok');
    expect(state.performance.value?.metrics.map((x) => x.key)).toEqual([
      'loginSuccess',
      'loginFailure',
      'accountLocked',
      'newAccount',
    ]);
  });

  it('defaults to the month', async () => {
    seedHappy();
    const state = await getIamOverviewState();
    expect(m.getIamMetrics).toHaveBeenCalledWith('month', 'wms');
    expect(state.period).toBe('month');
  });

  it('no active tenant → the metrics are not called either', async () => {
    m.getActiveTenant.mockResolvedValue(null);
    const state = await getIamOverviewState('week');
    expect(m.getIamMetrics).not.toHaveBeenCalled();
    expect(state.noActiveTenant).toBe(true);
    expect(state.period).toBe('week');
    expect(state.performance.status).not.toBe('ok');
  });

  it('403 (PERMISSION_DENIED / TENANT_SCOPE_DENIED) → strip forbidden, every card still ok', async () => {
    for (const code of ['PERMISSION_DENIED', 'TENANT_SCOPE_DENIED']) {
      seedHappy();
      m.getIamMetrics.mockRejectedValue(new ApiError(403, code, 'no'));
      const state = await getIamOverviewState();
      expect(state.performance, code).toEqual({ status: 'forbidden', value: null });
      expect(state.operators.status).toBe('ok');
      expect(state.accounts.total).toBe(123);
      expect(state.audit.total).toBe(57);
    }
  });

  it('503 / unparseable body → strip degraded (never zeros), every card still ok', async () => {
    seedHappy();
    m.getIamMetrics.mockRejectedValue(
      new AuditUnavailableError('downstream', 'DOWNSTREAM_ERROR', 'down'),
    );
    let state = await getIamOverviewState();
    expect(state.performance).toEqual({ status: 'degraded', value: null });
    expect(state.operators.total).toBe(10);

    seedHappy();
    m.getIamMetrics.mockResolvedValue({ ...METRICS, metrics: [{ ...METRICS.metrics[0], current: 412 }] });
    state = await getIamOverviewState();
    expect(state.performance).toEqual({ status: 'degraded', value: null });
    expect(state.audit.status).toBe('ok');
  });

  it('a strip 401 → whole-session redirect(/login), like every other leg', async () => {
    seedHappy();
    m.getIamMetrics.mockRejectedValue(new ApiError(401, 'TOKEN_INVALID', 'exp'));
    await expect(getIamOverviewState()).rejects.toThrow('REDIRECT:/login');
    expect(redirectMock).toHaveBeenCalledWith('/login?error=session_expired');
  });
});

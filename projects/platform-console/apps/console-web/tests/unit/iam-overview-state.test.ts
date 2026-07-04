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
}));
vi.mock('@/shared/lib/session', () => ({ getActiveTenant: m.getActiveTenant }));
vi.mock('@/features/operators/api/operators-api', () => ({
  listOperators: m.listOperators,
}));
vi.mock('@/features/accounts/api/accounts-api', () => ({
  searchAccounts: m.searchAccounts,
}));
vi.mock('@/features/audit/api/audit-api', () => ({ queryAudit: m.queryAudit }));

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
}

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
    expect(redirectMock).toHaveBeenCalledWith('/login');
  });
});

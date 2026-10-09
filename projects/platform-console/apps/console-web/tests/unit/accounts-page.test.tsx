import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from '@testing-library/react';

/**
 * `/accounts` SSR gating waterfall (TASK-PC-FE-326) — mirrors the
 * `/tenants` page test conventions (`tenants-page.test.tsx`).
 *
 * TASK-PC-FE-326's core behaviour: `getAccountsAccessTier()` is checked
 * FIRST. `'search-only'` renders the email-search-only screen WITHOUT ever
 * calling `getAccountsListState()` (the bite below proves this — the
 * unfiltered list would only 403 for that caller). `'full'`/`'none'` fall
 * through to the unchanged `getAccountsListState()` waterfall (noTenant /
 * forbidden / degraded / success).
 */

const getAccountsListState = vi.fn();
const getAccountsAccessTier = vi.fn();
const getAccountsSearchOnlyState = vi.fn();
vi.mock('@/features/accounts', () => ({
  getAccountsListState: (a: unknown) => getAccountsListState(a),
  getAccountsAccessTier: () => getAccountsAccessTier(),
  getAccountsSearchOnlyState: () => getAccountsSearchOnlyState(),
  AccountsScreen: ({
    initial,
    searchOnly,
  }: {
    initial: { content: unknown[] } | null;
    searchOnly?: boolean;
  }) => (
    <div
      data-testid="accounts-screen"
      data-initial={initial ? 'present' : 'null'}
      data-search-only={searchOnly ? 'true' : 'false'}
    />
  ),
}));

// Same no-tenant kind seam `tenants-page.test.tsx` mocks — `NoTenantNotice`
// (shared widget) reads it internally.
let noTenantNoticeKindResult: 'zero' | 'select' = 'select';
vi.mock('@/shared/lib/active-tenant-default', () => ({
  noTenantNoticeKind: async () => noTenantNoticeKindResult,
}));
vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

import AccountsPage from '@/app/(console)/accounts/page';

beforeEach(() => {
  getAccountsListState.mockReset();
  getAccountsAccessTier.mockReset();
  getAccountsSearchOnlyState.mockReset();
  noTenantNoticeKindResult = 'select';
});

describe('AccountsPage — search-only tier (TASK-PC-FE-326)', () => {
  it('SUPPORT_LOCK (tier=search-only) renders the search-only AccountsScreen — NOT the forbidden notice', async () => {
    getAccountsAccessTier.mockResolvedValue('search-only');
    getAccountsSearchOnlyState.mockResolvedValue({ noTenant: false });

    const ui = await AccountsPage();
    const { getByTestId, queryByTestId } = render(ui);

    const screen = getByTestId('accounts-screen');
    expect(screen).toHaveAttribute('data-search-only', 'true');
    expect(screen).toHaveAttribute('data-initial', 'null');
    expect(queryByTestId('accounts-forbidden')).not.toBeInTheDocument();
    // bite target: the unfiltered list must never be called for this tier —
    // it would only 403 for a caller with no account.read.
    expect(getAccountsListState).not.toHaveBeenCalled();
  });

  it('search-only + no active tenant → the same no-tenant gate as the full-list path', async () => {
    getAccountsAccessTier.mockResolvedValue('search-only');
    getAccountsSearchOnlyState.mockResolvedValue({ noTenant: true });

    const ui = await AccountsPage();
    const { getByTestId } = render(ui);
    expect(getByTestId('accounts-no-tenant')).toBeInTheDocument();
    expect(getAccountsListState).not.toHaveBeenCalled();
  });
});

describe('AccountsPage — full / none tiers (unchanged behaviour)', () => {
  it('a role holding none of the four permissions (tier=none) still gets the forbidden notice', async () => {
    getAccountsAccessTier.mockResolvedValue('none');
    getAccountsListState.mockResolvedValue({
      page: null,
      degraded: false,
      noTenant: false,
      forbidden: true,
      query: {},
    });

    const ui = await AccountsPage();
    const { getByTestId } = render(ui);
    expect(getByTestId('accounts-forbidden')).toBeInTheDocument();
    expect(getAccountsListState).toHaveBeenCalledWith({ page: 0, size: 20 });
  });

  it('SUPPORT_READONLY / SUPER_ADMIN (tier=full) render the full AccountsScreen with the seeded page — unchanged', async () => {
    getAccountsAccessTier.mockResolvedValue('full');
    getAccountsListState.mockResolvedValue({
      page: { content: [{ id: 'a' }], page: 0, size: 20, totalElements: 1, totalPages: 1 },
      degraded: false,
      noTenant: false,
      forbidden: false,
      query: {},
    });

    const ui = await AccountsPage();
    const { getByTestId } = render(ui);
    const screen = getByTestId('accounts-screen');
    expect(screen).toHaveAttribute('data-initial', 'present');
    expect(screen).toHaveAttribute('data-search-only', 'false');
  });

  it('tier=full + no active tenant → the no-tenant gate (unchanged)', async () => {
    getAccountsAccessTier.mockResolvedValue('full');
    getAccountsListState.mockResolvedValue({
      page: null,
      degraded: false,
      noTenant: true,
      forbidden: false,
      query: {},
    });

    const ui = await AccountsPage();
    const { getByTestId } = render(ui);
    expect(getByTestId('accounts-no-tenant')).toBeInTheDocument();
  });

  it('tier=full + 503/timeout → the degraded notice (unchanged)', async () => {
    getAccountsAccessTier.mockResolvedValue('full');
    getAccountsListState.mockResolvedValue({
      page: null,
      degraded: true,
      noTenant: false,
      forbidden: false,
      query: {},
    });

    const ui = await AccountsPage();
    const { getByTestId } = render(ui);
    expect(getByTestId('accounts-degraded')).toBeInTheDocument();
  });
});

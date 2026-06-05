import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';

/**
 * TASK-PC-FE-054 — `<DelegationScreen>` component tests. Asserts:
 *   - renders two lists (내가 위임한 / 나에게 위임된);
 *   - status badge ACTIVE=활성 / REVOKED=회수됨 / expired ACTIVE=만료;
 *   - absent validTo renders as "무기한";
 *   - revoke action only on ACTIVE (non-expired) delegator grants;
 *   - create dialog gates required fields + submits with Idempotency-Key;
 *   - revoke dialog gates reason-required + submits with reason + key;
 *   - delegation error codes map inline (no crash).
 *
 * Same-origin `/api/erp/approval/delegations**` fetch mocked.
 */

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: vi.fn() }),
  usePathname: () => '/erp',
  useSearchParams: () => new URLSearchParams(),
}));

import { DelegationScreen } from '@/features/erp-ops';

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function errorResponse(code: string, status: number) {
  return new Response(JSON.stringify({ code, message: 'e' }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

// ---------------------------------------------------------------------------
// Test fixtures.
// ---------------------------------------------------------------------------

const ACTIVE_GRANT_DELEGATOR = {
  id: 'del-active',
  delegatorId: 'emp-me',
  delegateId: 'emp-delegate',
  validFrom: '2026-06-01T00:00:00Z',
  status: 'ACTIVE',
  createdAt: '2026-06-01T00:00:00Z',
  createdBy: 'emp-me',
  // validTo ABSENT — open-ended → "무기한".
};

const REVOKED_GRANT_DELEGATOR = {
  id: 'del-revoked',
  delegatorId: 'emp-me',
  delegateId: 'emp-delegate-2',
  validFrom: '2026-05-01T00:00:00Z',
  status: 'REVOKED',
  createdAt: '2026-05-01T00:00:00Z',
  createdBy: 'emp-me',
  revokedAt: '2026-05-20T00:00:00Z',
  revokedBy: 'emp-me',
};

const ACTIVE_GRANT_DELEGATE = {
  id: 'del-me-as-delegate',
  delegatorId: 'emp-boss',
  delegateId: 'emp-me',
  validFrom: '2026-06-01T00:00:00Z',
  validTo: '2026-06-30T23:59:59Z',
  status: 'ACTIVE',
  createdAt: '2026-06-01T00:00:00Z',
  createdBy: 'emp-boss',
};

const DELEGATOR_LIST = {
  data: [ACTIVE_GRANT_DELEGATOR, REVOKED_GRANT_DELEGATOR],
  meta: { page: 0, size: 20, totalElements: 2 },
};

const DELEGATE_LIST = {
  data: [ACTIVE_GRANT_DELEGATE],
  meta: { page: 0, size: 20, totalElements: 1 },
};

const EMPTY_LIST = {
  data: [],
  meta: { page: 0, size: 20, totalElements: 0 },
};

/** Mock fetch that returns delegator/delegate lists by role query param.
 *  URLs may be relative (e.g. `/api/erp/...?role=DELEGATOR`) so we use
 *  string matching rather than `new URL()` to avoid "invalid URL" throws. */
function makeFetchMock(
  delegatorList = DELEGATOR_LIST,
  delegateList = DELEGATE_LIST,
) {
  return vi.fn((url: string) => {
    if (String(url).includes('role=DELEGATE') && !String(url).includes('role=DELEGATOR')) {
      return Promise.resolve(jsonResponse(delegateList));
    }
    // Default (DELEGATOR or no role) → return delegator list.
    return Promise.resolve(jsonResponse(delegatorList));
  });
}

beforeEach(() => {
  vi.unstubAllGlobals();
});

// ===========================================================================
// Screen renders two lists.
// ===========================================================================

describe('DelegationScreen — renders both lists', () => {
  it('renders delegation-screen + both list sections', async () => {
    vi.stubGlobal('fetch', makeFetchMock());
    render(<DelegationScreen />, { wrapper: wrapper() });
    expect(screen.getByTestId('delegation-screen')).toBeInTheDocument();
    expect(screen.getByTestId('delegation-list-delegator')).toBeInTheDocument();
    expect(screen.getByTestId('delegation-list-delegate')).toBeInTheDocument();
  });

  it('delegator list shows active grant row + revoke button', async () => {
    vi.stubGlobal('fetch', makeFetchMock());
    render(<DelegationScreen />, { wrapper: wrapper() });
    // Wait for data.
    await waitFor(() =>
      expect(
        screen.getByTestId(`delegation-row-${ACTIVE_GRANT_DELEGATOR.id}`),
      ).toBeInTheDocument(),
    );
    // delegateId shown.
    expect(
      screen.getByTestId(`delegation-row-${ACTIVE_GRANT_DELEGATOR.id}`).textContent,
    ).toContain('emp-delegate');
    // Revoke button exists for ACTIVE grant in delegator list.
    expect(
      screen.getByTestId(`delegation-revoke-${ACTIVE_GRANT_DELEGATOR.id}`),
    ).toBeInTheDocument();
  });

  it('REVOKED grant in delegator list has no revoke button', async () => {
    vi.stubGlobal('fetch', makeFetchMock());
    render(<DelegationScreen />, { wrapper: wrapper() });
    await waitFor(() =>
      expect(
        screen.getByTestId(`delegation-row-${REVOKED_GRANT_DELEGATOR.id}`),
      ).toBeInTheDocument(),
    );
    expect(
      screen.queryByTestId(`delegation-revoke-${REVOKED_GRANT_DELEGATOR.id}`),
    ).not.toBeInTheDocument();
  });

  it('delegate list shows grants with delegatorId + no revoke button', async () => {
    vi.stubGlobal('fetch', makeFetchMock());
    render(<DelegationScreen />, { wrapper: wrapper() });
    await waitFor(() =>
      expect(
        screen.getByTestId(`delegation-row-${ACTIVE_GRANT_DELEGATE.id}`),
      ).toBeInTheDocument(),
    );
    expect(
      screen.getByTestId(`delegation-row-${ACTIVE_GRANT_DELEGATE.id}`).textContent,
    ).toContain('emp-boss');
    // No revoke button in delegate list.
    expect(
      screen.queryByTestId(`delegation-revoke-${ACTIVE_GRANT_DELEGATE.id}`),
    ).not.toBeInTheDocument();
  });

  it('empty delegator list shows empty notice (no crash)', async () => {
    vi.stubGlobal('fetch', makeFetchMock(EMPTY_LIST, EMPTY_LIST));
    render(<DelegationScreen />, { wrapper: wrapper() });
    await waitFor(() => {
      const delegatorSection = screen.getByTestId('delegation-list-delegator');
      expect(delegatorSection.textContent).toContain('없습니다');
    });
  });
});

// ===========================================================================
// Status badge.
// ===========================================================================

describe('DelegationScreen — status badges', () => {
  it('ACTIVE grant → "활성" badge', async () => {
    vi.stubGlobal('fetch', makeFetchMock());
    render(<DelegationScreen />, { wrapper: wrapper() });
    await waitFor(() =>
      expect(
        screen.getByTestId(`delegation-row-${ACTIVE_GRANT_DELEGATOR.id}`),
      ).toBeInTheDocument(),
    );
    // The ACTIVE_GRANT_DELEGATOR row should have an "활성" badge.
    const row = screen.getByTestId(`delegation-row-${ACTIVE_GRANT_DELEGATOR.id}`);
    const badges = row.querySelectorAll('[data-testid="delegation-status-badge"]');
    const activeBadge = Array.from(badges).find(
      (b) => b.getAttribute('data-status') === 'ACTIVE',
    );
    expect(activeBadge).toBeTruthy();
    expect(activeBadge!.textContent).toBe('활성');
  });

  it('REVOKED grant → "회수됨" badge', async () => {
    vi.stubGlobal('fetch', makeFetchMock());
    render(<DelegationScreen />, { wrapper: wrapper() });
    await waitFor(() =>
      expect(
        screen.getByTestId(`delegation-row-${REVOKED_GRANT_DELEGATOR.id}`),
      ).toBeInTheDocument(),
    );
    const row = screen.getByTestId(`delegation-row-${REVOKED_GRANT_DELEGATOR.id}`);
    const badges = row.querySelectorAll('[data-testid="delegation-status-badge"]');
    const revokedBadge = Array.from(badges).find(
      (b) => b.getAttribute('data-status') === 'REVOKED',
    );
    expect(revokedBadge).toBeTruthy();
    expect(revokedBadge!.textContent).toBe('회수됨');
  });

  it('ACTIVE grant with past validTo → "만료" badge + NO revoke button', async () => {
    // Past validTo — expired ACTIVE grant.
    const expiredGrant = {
      id: 'del-expired',
      delegatorId: 'emp-me',
      delegateId: 'emp-delegate',
      validFrom: '2020-01-01T00:00:00Z',
      validTo: '2020-12-31T23:59:59Z', // in the past
      status: 'ACTIVE',
      createdAt: '2020-01-01T00:00:00Z',
      createdBy: 'emp-me',
    };
    vi.stubGlobal(
      'fetch',
      makeFetchMock(
        {
          data: [expiredGrant],
          meta: { page: 0, size: 20, totalElements: 1 },
        },
        EMPTY_LIST,
      ),
    );
    render(<DelegationScreen />, { wrapper: wrapper() });
    await waitFor(() =>
      expect(
        screen.getByTestId(`delegation-row-${expiredGrant.id}`),
      ).toBeInTheDocument(),
    );
    const row = screen.getByTestId(`delegation-row-${expiredGrant.id}`);
    const badges = row.querySelectorAll('[data-testid="delegation-status-badge"]');
    const expiredBadge = Array.from(badges).find(
      (b) => b.getAttribute('data-status') === 'ACTIVE_EXPIRED',
    );
    expect(expiredBadge).toBeTruthy();
    expect(expiredBadge!.textContent).toBe('만료');
    // No revoke button on expired grant.
    expect(
      screen.queryByTestId(`delegation-revoke-${expiredGrant.id}`),
    ).not.toBeInTheDocument();
  });
});

// ===========================================================================
// Absent validTo → "무기한".
// ===========================================================================

describe('DelegationScreen — absent validTo renders as "무기한"', () => {
  it('row with absent validTo shows "무기한" in the period text', async () => {
    vi.stubGlobal('fetch', makeFetchMock());
    render(<DelegationScreen />, { wrapper: wrapper() });
    await waitFor(() =>
      expect(
        screen.getByTestId(`delegation-row-${ACTIVE_GRANT_DELEGATOR.id}`),
      ).toBeInTheDocument(),
    );
    const row = screen.getByTestId(
      `delegation-row-${ACTIVE_GRANT_DELEGATOR.id}`,
    );
    expect(row.textContent).toContain('무기한');
  });
});

// ===========================================================================
// Create dialog.
// ===========================================================================

describe('DelegationScreen — create dialog', () => {
  it('create button opens the dialog; submit is disabled until required fields filled', async () => {
    vi.stubGlobal('fetch', makeFetchMock());
    const user = userEvent.setup();
    render(<DelegationScreen />, { wrapper: wrapper() });
    await user.click(screen.getByTestId('delegation-create'));
    expect(screen.getByTestId('delegation-create-dialog')).toBeInTheDocument();
    expect(screen.getByTestId('delegation-create-submit')).toBeDisabled();
    // Fill delegateId.
    await user.type(screen.getByTestId('delegation-create-delegateId'), 'emp-delegate');
    // Still disabled (no validFrom).
    expect(screen.getByTestId('delegation-create-submit')).toBeDisabled();
    // Fill validFrom.
    await user.type(screen.getByTestId('delegation-create-validFrom'), '2026-06-10');
    expect(screen.getByTestId('delegation-create-submit')).toBeEnabled();
  });

  it('submit sends delegateId + validFrom + idempotencyKey to /api/erp/approval/delegations; closes on success', async () => {
    const delegatorList = {
      data: [ACTIVE_GRANT_DELEGATOR],
      meta: { page: 0, size: 20, totalElements: 1 },
    };
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if ((init as RequestInit)?.method === 'POST') {
        // POST create → return the grant.
        return Promise.resolve(jsonResponse(ACTIVE_GRANT_DELEGATOR, 201));
      }
      if (String(url).includes('role=DELEGATE') && !String(url).includes('role=DELEGATOR')) {
        return Promise.resolve(jsonResponse(EMPTY_LIST));
      }
      return Promise.resolve(jsonResponse(delegatorList));
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<DelegationScreen />, { wrapper: wrapper() });
    await user.click(screen.getByTestId('delegation-create'));
    await user.type(screen.getByTestId('delegation-create-delegateId'), 'emp-delegate');
    await user.type(screen.getByTestId('delegation-create-validFrom'), '2026-06-10');
    await user.click(screen.getByTestId('delegation-create-submit'));
    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some(
          (c) =>
            String(c[0]).endsWith('/delegations') &&
            (c[1] as RequestInit).method === 'POST',
        ),
      ).toBe(true),
    );
    // Verify body.
    const createCall = fetchMock.mock.calls.find(
      (c) =>
        String(c[0]).endsWith('/delegations') &&
        (c[1] as RequestInit).method === 'POST',
    )!;
    const body = JSON.parse((createCall[1] as RequestInit).body as string);
    expect(body.delegateId).toBe('emp-delegate');
    expect(body.validFrom).toBe('2026-06-10');
    expect(typeof body.idempotencyKey).toBe('string');
    expect(body.idempotencyKey.length).toBeGreaterThan(0);
    // Dialog closed on success.
    await waitFor(() =>
      expect(
        screen.queryByTestId('delegation-create-dialog'),
      ).not.toBeInTheDocument(),
    );
  });

  it('create error 422 DELEGATION_INVALID → inline error message (no crash)', async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if ((init as RequestInit)?.method === 'POST') {
        return Promise.resolve(errorResponse('DELEGATION_INVALID', 422));
      }
      if (String(url).includes('role=DELEGATE') && !String(url).includes('role=DELEGATOR')) {
        return Promise.resolve(jsonResponse(EMPTY_LIST));
      }
      return Promise.resolve(jsonResponse(DELEGATOR_LIST));
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<DelegationScreen />, { wrapper: wrapper() });
    await user.click(screen.getByTestId('delegation-create'));
    await user.type(screen.getByTestId('delegation-create-delegateId'), 'emp-self');
    await user.type(screen.getByTestId('delegation-create-validFrom'), '2026-06-10');
    await user.click(screen.getByTestId('delegation-create-submit'));
    const errEl = await screen.findByTestId('delegation-error');
    expect(errEl.textContent).toContain('자기 위임');
    // Dialog still mounted (no crash).
    expect(screen.getByTestId('delegation-create-dialog')).toBeInTheDocument();
  });
});

// ===========================================================================
// Revoke dialog.
// ===========================================================================

describe('DelegationScreen — revoke dialog', () => {
  it('revoke button opens dialog; confirm disabled until reason provided; submits with reason + idempotencyKey', async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if ((init as RequestInit)?.method === 'POST') {
        // revoke → return REVOKED grant.
        return Promise.resolve(
          jsonResponse({ ...ACTIVE_GRANT_DELEGATOR, status: 'REVOKED' }),
        );
      }
      if (String(url).includes('role=DELEGATE') && !String(url).includes('role=DELEGATOR')) {
        return Promise.resolve(jsonResponse(EMPTY_LIST));
      }
      return Promise.resolve(jsonResponse(DELEGATOR_LIST));
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<DelegationScreen />, { wrapper: wrapper() });
    // Wait for the revoke button to appear.
    await waitFor(() =>
      expect(
        screen.getByTestId(`delegation-revoke-${ACTIVE_GRANT_DELEGATOR.id}`),
      ).toBeInTheDocument(),
    );
    await user.click(
      screen.getByTestId(`delegation-revoke-${ACTIVE_GRANT_DELEGATOR.id}`),
    );
    expect(screen.getByTestId('delegation-revoke-dialog')).toBeInTheDocument();
    expect(screen.getByTestId('delegation-revoke-confirm')).toBeDisabled();
    await user.type(screen.getByTestId('delegation-revoke-reason'), '귀국');
    expect(screen.getByTestId('delegation-revoke-confirm')).toBeEnabled();
    await user.click(screen.getByTestId('delegation-revoke-confirm'));
    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some(
          (c) =>
            String(c[0]).includes('/revoke') &&
            (c[1] as RequestInit).method === 'POST',
        ),
      ).toBe(true),
    );
    const revokeCall = fetchMock.mock.calls.find(
      (c) =>
        String(c[0]).includes('/revoke') &&
        (c[1] as RequestInit).method === 'POST',
    )!;
    const body = JSON.parse((revokeCall[1] as RequestInit).body as string);
    expect(body.reason).toBe('귀국');
    expect(typeof body.idempotencyKey).toBe('string');
    // Dialog closed after success.
    await waitFor(() =>
      expect(
        screen.queryByTestId('delegation-revoke-dialog'),
      ).not.toBeInTheDocument(),
    );
  });

  it('revoke 404 DELEGATION_NOT_FOUND → inline error (no crash)', async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if ((init as RequestInit)?.method === 'POST') {
        return Promise.resolve(errorResponse('DELEGATION_NOT_FOUND', 404));
      }
      if (String(url).includes('role=DELEGATE') && !String(url).includes('role=DELEGATOR')) {
        return Promise.resolve(jsonResponse(EMPTY_LIST));
      }
      return Promise.resolve(jsonResponse(DELEGATOR_LIST));
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<DelegationScreen />, { wrapper: wrapper() });
    await waitFor(() =>
      expect(
        screen.getByTestId(`delegation-revoke-${ACTIVE_GRANT_DELEGATOR.id}`),
      ).toBeInTheDocument(),
    );
    await user.click(
      screen.getByTestId(`delegation-revoke-${ACTIVE_GRANT_DELEGATOR.id}`),
    );
    await user.type(screen.getByTestId('delegation-revoke-reason'), '사유');
    await user.click(screen.getByTestId('delegation-revoke-confirm'));
    const errEl = await screen.findByTestId('delegation-error');
    expect(errEl.textContent).toContain('찾을 수 없습니다');
    // Dialog still mounted (no crash).
    expect(screen.getByTestId('delegation-revoke-dialog')).toBeInTheDocument();
  });
});

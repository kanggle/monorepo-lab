import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';

/**
 * `<DelegationFactCard>` component test (TASK-PC-FE-055 — ADR-MONO-013 §D3.1).
 *
 * Headline assertions:
 *   - **meta.warning banner**: when the response carries `meta.warning`,
 *     a banner is rendered (AC-2 eventually-consistent hint).
 *   - **ABSENT fields graceful no-crash**: validFrom/validTo/reason/revokedAt
 *     may be absent (NON_NULL-absent); the card renders without throwing and
 *     shows "—" for validFrom and "무기한" for validTo when absent (AC-2).
 *   - **status badge**: ACTIVE → "활성", REVOKED → "회수됨" (AC-1).
 *   - **filter inputs**: delegatorId/delegateId/status/activeAt inputs exist (AC-1).
 *   - **pagination**: prev/next buttons present when data rows exist (AC-1).
 *   - **proxy GET-only**: the route modules for list + detail export only `GET`
 *     (no POST/PATCH/DELETE exports — AC-3).
 *   - **READ-ONLY**: no create/revoke buttons anywhere in the card (AC-4).
 */

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: vi.fn() }),
  usePathname: () => '/erp',
  useSearchParams: () => new URLSearchParams(),
}));

import { DelegationFactCard } from '@/features/erp-ops/components/DelegationFactCard';
import type { DelegationFactListResponse } from '@/features/erp-ops/api/types';

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const ACTIVE_FACT: DelegationFactListResponse['data'][number] = {
  grantId: 'dgr-001',
  status: 'ACTIVE',
  delegatorId: 'emp-A-001',
  delegateId: 'emp-D-001',
  validFrom: '2026-06-01T00:00:00Z',
  // validTo absent = open-ended / 무기한
  reason: '출장 대행',
};

const REVOKED_FACT: DelegationFactListResponse['data'][number] = {
  grantId: 'dgr-002',
  status: 'REVOKED',
  delegatorId: 'emp-A-002',
  delegateId: 'emp-D-002',
  validFrom: '2026-05-01T00:00:00Z',
  validTo: '2026-05-31T23:59:59Z',
  revokedAt: '2026-05-20T10:00:00Z',
};

/** Row with ALL optional fields absent (out-of-order revoke-before-grant
 *  scenario: validFrom absent, validTo absent, reason absent). */
const ALL_ABSENT_FACT: DelegationFactListResponse['data'][number] = {
  grantId: 'dgr-003',
  status: 'REVOKED',
  delegatorId: 'emp-A-003',
  delegateId: 'emp-D-003',
  // validFrom, validTo, reason, revokedAt all ABSENT
};

function makeListResp(
  rows: DelegationFactListResponse['data'],
  warning = 'Eventually-consistent read-model',
): DelegationFactListResponse {
  return {
    data: rows,
    meta: {
      page: 0,
      size: 20,
      totalElements: rows.length,
      timestamp: '2026-06-06T00:00:00Z',
      ...(warning ? { warning } : {}),
    },
  };
}

// ---------------------------------------------------------------------------
// Tests — meta.warning banner (AC-2)
// ---------------------------------------------------------------------------

describe('<DelegationFactCard> — meta.warning banner', () => {
  it('shows the eventually-consistent warning banner when meta.warning is present', () => {
    const initial = makeListResp([ACTIVE_FACT]);
    render(<DelegationFactCard initial={initial} />, { wrapper: wrapper() });

    const banner = screen.getByTestId('delegation-fact-warning');
    expect(banner).toBeTruthy();
    expect(banner.textContent).toContain('Eventually-consistent read-model');
  });

  it('does NOT show a warning banner when meta.warning is absent', () => {
    const initial: DelegationFactListResponse = {
      data: [ACTIVE_FACT],
      meta: {
        page: 0,
        size: 20,
        totalElements: 1,
        timestamp: 't',
        // no warning field
      },
    };
    render(<DelegationFactCard initial={initial} />, { wrapper: wrapper() });
    expect(screen.queryByTestId('delegation-fact-warning')).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// Tests — ABSENT fields graceful no-crash (AC-2)
// ---------------------------------------------------------------------------

describe('<DelegationFactCard> — ABSENT fields graceful (AC-2)', () => {
  it('renders without crash when all optional fields are absent', () => {
    const initial = makeListResp([ALL_ABSENT_FACT]);
    expect(() =>
      render(<DelegationFactCard initial={initial} />, { wrapper: wrapper() }),
    ).not.toThrow();
    // Table row is rendered (not an error page).
    expect(screen.getByTestId('delegation-fact-row-0')).toBeTruthy();
  });

  it('shows "무기한" when validTo is absent (open-ended delegation)', () => {
    const initial = makeListResp([ACTIVE_FACT]);
    render(<DelegationFactCard initial={initial} />, { wrapper: wrapper() });

    const validToCell = screen.getByTestId('delegation-fact-validTo-0');
    expect(validToCell.textContent).toBe('무기한');
  });

  it('shows "—" when validFrom is absent', () => {
    const initial = makeListResp([ALL_ABSENT_FACT]);
    render(<DelegationFactCard initial={initial} />, { wrapper: wrapper() });

    const validFromCell = screen.getByTestId('delegation-fact-validFrom-0');
    expect(validFromCell.textContent).toBe('—');
  });

  it('shows "무기한" when validTo is absent (ALL_ABSENT_FACT)', () => {
    const initial = makeListResp([ALL_ABSENT_FACT]);
    render(<DelegationFactCard initial={initial} />, { wrapper: wrapper() });

    const validToCell = screen.getByTestId('delegation-fact-validTo-0');
    expect(validToCell.textContent).toBe('무기한');
  });

  it('reason cell is empty (not crashing) when reason is absent', () => {
    const initial = makeListResp([ALL_ABSENT_FACT]);
    render(<DelegationFactCard initial={initial} />, { wrapper: wrapper() });

    const reasonCell = screen.getByTestId('delegation-fact-reason-0');
    expect(reasonCell.textContent).toBe('');
  });

  it('revokedAt cell is empty (not crashing) when revokedAt is absent', () => {
    const initial = makeListResp([ALL_ABSENT_FACT]);
    render(<DelegationFactCard initial={initial} />, { wrapper: wrapper() });

    const revokedAtCell = screen.getByTestId('delegation-fact-revokedAt-0');
    expect(revokedAtCell.textContent).toBe('');
  });

  it('renders revokedAt when REVOKED fact has revokedAt', () => {
    const initial = makeListResp([REVOKED_FACT]);
    render(<DelegationFactCard initial={initial} />, { wrapper: wrapper() });

    const revokedAtCell = screen.getByTestId('delegation-fact-revokedAt-0');
    expect(revokedAtCell.textContent).toContain('2026-05-20');
  });

  it('renders reason when present', () => {
    const initial = makeListResp([ACTIVE_FACT]);
    render(<DelegationFactCard initial={initial} />, { wrapper: wrapper() });

    const reasonCell = screen.getByTestId('delegation-fact-reason-0');
    expect(reasonCell.textContent).toContain('출장 대행');
  });
});

// ---------------------------------------------------------------------------
// Tests — status badge (AC-1)
// ---------------------------------------------------------------------------

describe('<DelegationFactCard> — status badge', () => {
  it('ACTIVE fact renders "활성" badge with data-status="ACTIVE"', () => {
    const initial = makeListResp([ACTIVE_FACT]);
    render(<DelegationFactCard initial={initial} />, { wrapper: wrapper() });

    const badge = screen.getByTestId('delegation-fact-status-0');
    expect(badge.textContent).toBe('활성');
    expect(badge.getAttribute('data-status')).toBe('ACTIVE');
  });

  it('REVOKED fact renders "회수됨" badge with data-status="REVOKED"', () => {
    const initial = makeListResp([REVOKED_FACT]);
    render(<DelegationFactCard initial={initial} />, { wrapper: wrapper() });

    const badge = screen.getByTestId('delegation-fact-status-0');
    expect(badge.textContent).toBe('회수됨');
    expect(badge.getAttribute('data-status')).toBe('REVOKED');
  });

  it('renders both ACTIVE and REVOKED rows without crash', () => {
    const initial = makeListResp([ACTIVE_FACT, REVOKED_FACT]);
    expect(() =>
      render(<DelegationFactCard initial={initial} />, { wrapper: wrapper() }),
    ).not.toThrow();
    expect(screen.getByTestId('delegation-fact-row-0')).toBeTruthy();
    expect(screen.getByTestId('delegation-fact-row-1')).toBeTruthy();
  });
});

// ---------------------------------------------------------------------------
// Tests — filter inputs exist (AC-1)
// ---------------------------------------------------------------------------

describe('<DelegationFactCard> — filter inputs', () => {
  it('renders delegatorId, delegateId, status select, and activeAt filter inputs', () => {
    const initial = makeListResp([ACTIVE_FACT]);
    render(<DelegationFactCard initial={initial} />, { wrapper: wrapper() });

    expect(screen.getByTestId('delegation-fact-filter-delegatorId')).toBeTruthy();
    expect(screen.getByTestId('delegation-fact-filter-delegateId')).toBeTruthy();
    expect(screen.getByTestId('delegation-fact-filter-status')).toBeTruthy();
    expect(screen.getByTestId('delegation-fact-filter-activeAt')).toBeTruthy();
    expect(screen.getByTestId('delegation-fact-filter-apply')).toBeTruthy();
    expect(screen.getByTestId('delegation-fact-filter-clear')).toBeTruthy();
  });
});

// ---------------------------------------------------------------------------
// Tests — pagination (AC-1)
// ---------------------------------------------------------------------------

describe('<DelegationFactCard> — pagination', () => {
  it('renders prev/next buttons when there are data rows', () => {
    const initial = makeListResp([ACTIVE_FACT, REVOKED_FACT]);
    render(<DelegationFactCard initial={initial} />, { wrapper: wrapper() });

    expect(screen.getByTestId('delegation-fact-prev')).toBeTruthy();
    expect(screen.getByTestId('delegation-fact-next')).toBeTruthy();
  });

  it('shows page info text', () => {
    const initial = makeListResp([ACTIVE_FACT]);
    render(<DelegationFactCard initial={initial} />, { wrapper: wrapper() });

    const pageInfo = screen.getByTestId('delegation-fact-pageinfo');
    expect(pageInfo.textContent).toContain('1 / 1 페이지');
  });

  it('shows empty state when data is empty', () => {
    const initial = makeListResp([]);
    render(<DelegationFactCard initial={initial} />, { wrapper: wrapper() });

    expect(screen.getByTestId('delegation-fact-empty')).toBeTruthy();
    expect(screen.queryByTestId('delegation-fact-table')).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// Tests — proxy GET-only assertion (AC-3)
// The route modules must export only `GET` — no POST/PATCH/DELETE.
// ---------------------------------------------------------------------------

describe('Proxy routes — GET-only assertion (AC-3)', () => {
  it('delegation list proxy exports only GET (no POST/PATCH/DELETE)', async () => {
    const routeModule = await import(
      '@/app/api/erp/read-model/delegations/route'
    ) as Record<string, unknown>;
    expect(typeof routeModule['GET']).toBe('function');
    expect(routeModule['POST']).toBeUndefined();
    expect(routeModule['PATCH']).toBeUndefined();
    expect(routeModule['DELETE']).toBeUndefined();
    expect(routeModule['PUT']).toBeUndefined();
  });

  it('delegation detail proxy exports only GET (no POST/PATCH/DELETE)', async () => {
    const routeModule = await import(
      '@/app/api/erp/read-model/delegations/[grantId]/route'
    ) as Record<string, unknown>;
    expect(typeof routeModule['GET']).toBe('function');
    expect(routeModule['POST']).toBeUndefined();
    expect(routeModule['PATCH']).toBeUndefined();
    expect(routeModule['DELETE']).toBeUndefined();
    expect(routeModule['PUT']).toBeUndefined();
  });
});

// ---------------------------------------------------------------------------
// Tests — READ-ONLY: no create/revoke buttons (AC-4)
// ---------------------------------------------------------------------------

describe('<DelegationFactCard> — read-only, no write affordances (AC-4)', () => {
  it('renders no create/revoke buttons — only filter apply/clear + pagination buttons', () => {
    const initial = makeListResp([ACTIVE_FACT, REVOKED_FACT]);
    const { container } = render(
      <DelegationFactCard initial={initial} />,
      { wrapper: wrapper() },
    );
    const buttons = Array.from(container.querySelectorAll('button'));
    // Allowed buttons: 조회 / 초기화 / 이전 / 다음
    for (const btn of buttons) {
      expect(btn.textContent).toMatch(/조회|초기화|이전|다음/);
    }
  });
});

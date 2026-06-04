import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';

/**
 * `<EmployeeOrgViewCard>` component test (TASK-PC-FE-049 — § 2.4.8
 * *Integrated read-model binding*).
 *
 * Headline assertions:
 *   - **Department path breadcrumb**: the department `path` array is
 *     rendered as a ›-joined string ("HQ · 본사 › SALES · 영업본부").
 *   - **meta.warning banner**: when the response carries
 *     `meta.warning`, a banner is rendered (AC-4 eventually-consistent
 *     hint).
 *   - **"동기화 중" badge**: a row with `department: null` (unresolved
 *     reference) OR with the field name in `meta.unresolved` renders
 *     a "동기화 중" badge rather than crashing (AC-4).
 *   - **Empty projection**: an empty `data: []` response renders the
 *     empty state + eventually-consistent hint (AC-4; not a 404 error).
 *   - **READ-ONLY**: no write affordance (button/input/form) anywhere
 *     in the card — this is the E5 read-only surface.
 */

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: vi.fn() }),
  usePathname: () => '/erp',
  useSearchParams: () => new URLSearchParams(),
}));

// Stub the client-side api call (the component uses useEmployeeOrgViews
// which internally calls apiClient.get). We don't want real HTTP in unit
// tests — just provide initialData via the `initial` prop instead.

import { EmployeeOrgViewCard } from '@/features/erp-ops/components/EmployeeOrgViewCard';
import type { EmployeeOrgViewListResponse } from '@/features/erp-ops/api/types';

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

const RESOLVED_ROW: EmployeeOrgViewListResponse['data'][number] = {
  id: 'emp-org-1',
  employeeNumber: 'E-1001',
  name: '홍길동',
  status: 'ACTIVE',
  effectivePeriod: { effectiveFrom: '2026-01-01', effectiveTo: null },
  department: {
    id: 'dept-1',
    code: 'SALES',
    name: '영업본부',
    path: [
      { id: 'dept-root', code: 'HQ', name: '본사' },
      { id: 'dept-1', code: 'SALES', name: '영업본부' },
    ],
  },
  costCenter: { id: 'cc-1', code: 'CC-100', name: '영업원가센터' },
  jobGrade: { id: 'jg-1', code: 'G3', name: '사원', displayOrder: 30 },
};

/** Row where department is null (event not yet projected). */
const UNRESOLVED_DEPT_ROW: EmployeeOrgViewListResponse['data'][number] = {
  id: 'emp-org-2',
  employeeNumber: 'E-1002',
  name: '이순신',
  status: 'ACTIVE',
  effectivePeriod: { effectiveFrom: '2026-01-01', effectiveTo: null },
  department: null,
  costCenter: { id: 'cc-1', code: 'CC-100', name: '영업원가센터' },
  jobGrade: null,
};

function makeListResp(
  rows: EmployeeOrgViewListResponse['data'],
  unresolved?: string[],
): EmployeeOrgViewListResponse {
  return {
    data: rows,
    meta: {
      page: 0,
      size: 20,
      totalElements: rows.length,
      timestamp: '2026-06-04T00:00:00Z',
      warning: 'Eventually-consistent read-model',
      ...(unresolved ? { unresolved } : {}),
    },
  };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('<EmployeeOrgViewCard> — department path breadcrumb', () => {
  it('renders the department path as a ›-joined breadcrumb', () => {
    const initial = makeListResp([RESOLVED_ROW]);
    render(<EmployeeOrgViewCard initial={initial} />, { wrapper: wrapper() });

    // Department path: "HQ · 본사 › SALES · 영업본부"
    const deptCell = screen.getByTestId('erp-orgview-dept-0');
    expect(deptCell.textContent).toContain('HQ · 본사');
    expect(deptCell.textContent).toContain('SALES · 영업본부');
    expect(deptCell.textContent).toContain('›');
  });

  it('renders cost center and job grade text', () => {
    const initial = makeListResp([RESOLVED_ROW]);
    render(<EmployeeOrgViewCard initial={initial} />, { wrapper: wrapper() });

    const ccCell = screen.getByTestId('erp-orgview-cc-0');
    expect(ccCell.textContent).toContain('CC-100');
    expect(ccCell.textContent).toContain('영업원가센터');

    const jgCell = screen.getByTestId('erp-orgview-jg-0');
    expect(jgCell.textContent).toContain('G3');
    expect(jgCell.textContent).toContain('사원');
  });
});

describe('<EmployeeOrgViewCard> — meta.warning banner (AC-4)', () => {
  it('shows the eventually-consistent warning banner when meta.warning is present', () => {
    const initial = makeListResp([RESOLVED_ROW]);
    render(<EmployeeOrgViewCard initial={initial} />, { wrapper: wrapper() });

    const banner = screen.getByTestId('erp-orgview-warning');
    expect(banner).toBeTruthy();
    expect(banner.textContent).toContain('Eventually-consistent read-model');
  });

  it('does NOT show a warning banner when meta.warning is absent', () => {
    const initial: EmployeeOrgViewListResponse = {
      data: [RESOLVED_ROW],
      meta: {
        page: 0,
        size: 20,
        totalElements: 1,
        timestamp: 't',
        // no warning field
      },
    };
    render(<EmployeeOrgViewCard initial={initial} />, { wrapper: wrapper() });
    expect(screen.queryByTestId('erp-orgview-warning')).toBeNull();
  });
});

describe('<EmployeeOrgViewCard> — "동기화 중" badge for unresolved references (AC-4)', () => {
  it('shows "동기화 중" badge for a null department field', () => {
    const initial = makeListResp([UNRESOLVED_DEPT_ROW], ['department', 'jobGrade']);
    render(<EmployeeOrgViewCard initial={initial} />, { wrapper: wrapper() });

    // Department cell should show the sync badge, not a breadcrumb.
    const deptCell = screen.getByTestId('erp-orgview-dept-0');
    expect(deptCell.querySelector('[data-testid="erp-orgview-sync-badge"]')).toBeTruthy();
    expect(deptCell.textContent).toContain('동기화 중');
  });

  it('shows "동기화 중" badge for a null jobGrade field', () => {
    const initial = makeListResp([UNRESOLVED_DEPT_ROW], ['department', 'jobGrade']);
    render(<EmployeeOrgViewCard initial={initial} />, { wrapper: wrapper() });

    const jgCell = screen.getByTestId('erp-orgview-jg-0');
    expect(jgCell.querySelector('[data-testid="erp-orgview-sync-badge"]')).toBeTruthy();
  });

  it('does NOT crash when any reference field is null (no fabricated value)', () => {
    const initial = makeListResp([UNRESOLVED_DEPT_ROW]);
    // Should render without throwing.
    expect(() =>
      render(<EmployeeOrgViewCard initial={initial} />, { wrapper: wrapper() }),
    ).not.toThrow();
    // Table row is rendered (not an error page).
    expect(screen.getByTestId('erp-orgview-row-0')).toBeTruthy();
  });
});

describe('<EmployeeOrgViewCard> — empty projection (AC-4)', () => {
  it('renders the empty-state message when data is empty', () => {
    const initial = makeListResp([]);
    render(<EmployeeOrgViewCard initial={initial} />, { wrapper: wrapper() });

    const empty = screen.getByTestId('erp-orgview-empty');
    expect(empty).toBeTruthy();
    // Should mention eventual consistency since meta.warning is set.
    expect(empty.textContent).toContain('read-model');
  });

  it('renders the warning banner even when data is empty (eventual-consistency hint always present)', () => {
    const initial = makeListResp([]);
    render(<EmployeeOrgViewCard initial={initial} />, { wrapper: wrapper() });

    const banner = screen.getByTestId('erp-orgview-warning');
    expect(banner).toBeTruthy();
  });

  it('does NOT render the table when data is empty', () => {
    const initial = makeListResp([]);
    render(<EmployeeOrgViewCard initial={initial} />, { wrapper: wrapper() });
    expect(screen.queryByTestId('erp-orgview-table')).toBeNull();
  });
});

describe('<EmployeeOrgViewCard> — read-only (no write affordances, E5)', () => {
  it('renders no buttons for create/update/retire', () => {
    const initial = makeListResp([RESOLVED_ROW]);
    const { container } = render(
      <EmployeeOrgViewCard initial={initial} />,
      { wrapper: wrapper() },
    );
    // No create/update/retire buttons should be present.
    const buttons = container.querySelectorAll('button');
    // Only pagination buttons (이전 / 다음) are allowed.
    for (const btn of Array.from(buttons)) {
      expect(btn.textContent).toMatch(/이전|다음/);
    }
  });
});

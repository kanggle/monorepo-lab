import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';

/**
 * `<ErpOpsScreen>` + sub-components (TASK-PC-FE-010 / § 2.4.8).
 *
 * Asserts the four headline erp obligations end-to-end at the
 * component layer:
 *   - **E2 effective-period rendering**: a list including an
 *     `effectiveTo: null` (active) row AND an `effectiveTo: <past>`
 *     (retired) row renders BOTH (retired NOT hidden / filtered).
 *     The retired row is visually distinct (`data-retired="true"`
 *     attribute) but rendered.
 *   - **E1 reference integrity surfacing**: an employee detail
 *     whose `departmentId` resolves to a RETIRED department
 *     surfaces a `<RetiredReferenceBadge>` (NEVER silently
 *     sanitized).
 *   - **Honest enum surfacing**: a `RETIRED` master + a
 *     `SEPARATED` employee render their status as such (a chip,
 *     visually distinct). Unknown / future enum values render
 *     with a generic " (unknown)" suffix, no parser throw.
 *   - **Confidential**: the api module never logs tokens / PII /
 *     financial / sensitive attrs (a console spy asserts this
 *     across the render path — distinct from the api-level
 *     log-spy test).
 */

// Mock next/navigation so the AsOfPicker / useAsOf hook mount in
// the jsdom env.
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: vi.fn() }),
  usePathname: () => '/erp',
  useSearchParams: () => new URLSearchParams(),
  redirect: (to: string) => {
    throw new Error(`REDIRECT:${to}`);
  },
}));

import {
  DepartmentList,
  EmployeeList,
  EmployeeDetail,
  AsOfPicker,
} from '@/features/erp-ops';
import type {
  DepartmentListResponse,
  EmployeeListResponse,
  Employee,
  Department,
} from '@/features/erp-ops';

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

const ACTIVE_DEPT: DepartmentListResponse['data'][number] = {
  id: 'dept-active',
  code: 'DEPT-001',
  name: 'Sales (active)',
  parentId: null,
  status: 'ACTIVE',
  effectivePeriod: {
    effectiveFrom: '2026-01-01',
    effectiveTo: null,
  },
};
const RETIRED_DEPT: DepartmentListResponse['data'][number] = {
  id: 'dept-retired',
  code: 'DEPT-OLD',
  name: 'Legacy Sales (retired)',
  parentId: null,
  status: 'RETIRED',
  effectivePeriod: {
    effectiveFrom: '2025-01-01',
    effectiveTo: '2025-12-31',
  },
};
const DEPT_LIST: DepartmentListResponse = {
  data: [ACTIVE_DEPT, RETIRED_DEPT],
  meta: { page: 0, size: 20, totalElements: 2, timestamp: 'x' },
};

const EMPLOYED_EMP: EmployeeListResponse['data'][number] = {
  id: 'emp-employed',
  employeeNumber: 'EMP-001',
  name: 'Active Person',
  departmentId: 'dept-active',
  jobGradeId: 'jg-1',
  costCenterId: 'cc-1',
  status: 'ACTIVE',
  employmentStatus: 'EMPLOYED',
  effectivePeriod: { effectiveFrom: '2026-01-01', effectiveTo: null },
};
const SEPARATED_EMP: EmployeeListResponse['data'][number] = {
  id: 'emp-separated',
  employeeNumber: 'EMP-002',
  name: 'Separated Person',
  departmentId: 'dept-active',
  jobGradeId: 'jg-1',
  costCenterId: 'cc-1',
  status: 'ACTIVE',
  employmentStatus: 'SEPARATED',
  effectivePeriod: { effectiveFrom: '2026-01-01', effectiveTo: null },
};
const EMP_LIST: EmployeeListResponse = {
  data: [EMPLOYED_EMP, SEPARATED_EMP],
  meta: { page: 0, size: 20, totalElements: 2, timestamp: 'x' },
};

beforeEach(() => {
  vi.unstubAllGlobals();
});

// ===========================================================================
// E2 effective-period rendering — both active AND retired rendered;
// retired NOT hidden.
// ===========================================================================

describe('E2 — effective-period rendering (active vs retired both shown; retired NOT hidden; § 2.4.8)', () => {
  it('renders a list including a retired row — the retired row is in the DOM (NOT hidden / filtered)', () => {
    render(<DepartmentList initial={DEPT_LIST} />, { wrapper: wrapper() });
    // Both rows rendered — retired NOT filtered.
    expect(screen.getByTestId('erp-department-row-0')).toBeInTheDocument();
    expect(screen.getByTestId('erp-department-row-1')).toBeInTheDocument();
    // The retired row carries `data-retired="true"` (visually
    // distinct via row-level opacity) but is still in the DOM.
    const retiredRow = screen.getByTestId('erp-department-row-1');
    expect(retiredRow).toHaveAttribute('data-retired', 'true');
    // The active row is rendered without the retired marker.
    const activeRow = screen.getByTestId('erp-department-row-0');
    expect(activeRow).toHaveAttribute('data-retired', 'false');
  });

  it('the retired-row text is searchable in the document (honest surfacing — not silently dropped)', () => {
    render(<DepartmentList initial={DEPT_LIST} />, { wrapper: wrapper() });
    expect(screen.getByText('DEPT-OLD')).toBeInTheDocument();
    expect(screen.getByText('Legacy Sales (retired)')).toBeInTheDocument();
  });

  it('the `<EffectivePeriodBadge>` distinguishes active vs retired visually', () => {
    render(<DepartmentList initial={DEPT_LIST} />, { wrapper: wrapper() });
    // The active row's badge.
    const activeBadge = screen.getAllByTestId('erp-effective-active');
    expect(activeBadge.length).toBeGreaterThan(0);
    // The retired row's badge.
    const retiredBadge = screen.getAllByTestId('erp-effective-retired');
    expect(retiredBadge.length).toBeGreaterThan(0);
  });
});

// ===========================================================================
// Honest enum surfacing — RETIRED master + SEPARATED employee both
// rendered with status chips; unknown enum → generic label, no throw.
// ===========================================================================

describe('honest enum surfacing — RETIRED master + SEPARATED employee rendered honestly (§ 2.4.8)', () => {
  it('a RETIRED master status chip is rendered as such (not hidden)', () => {
    render(<DepartmentList initial={DEPT_LIST} />, { wrapper: wrapper() });
    const retiredChip = screen.getByTestId('erp-department-status-1');
    expect(retiredChip.textContent).toContain('RETIRED');
  });

  it('a SEPARATED employee renders the employment-status chip visually distinct (NOT hidden)', () => {
    render(<EmployeeList initial={EMP_LIST} />, { wrapper: wrapper() });
    expect(screen.getByTestId('erp-employee-row-1')).toHaveAttribute(
      'data-separated',
      'true',
    );
    const chip = screen.getByTestId('erp-employee-employment-1');
    expect(chip.textContent).toContain('SEPARATED');
  });

  it('an unknown / future enum renders with a generic " (unknown)" suffix — NO throw', () => {
    const exotic: DepartmentListResponse = {
      data: [
        {
          ...ACTIVE_DEPT,
          id: 'dept-future',
          status: 'SUSPENDED_FUTURE_X',
        } as Department,
      ],
      meta: { page: 0, size: 20, totalElements: 1, timestamp: 'x' },
    };
    expect(() =>
      render(<DepartmentList initial={exotic} />, { wrapper: wrapper() }),
    ).not.toThrow();
    const chip = screen.getByTestId('erp-department-status-0');
    expect(chip.textContent).toContain('SUSPENDED_FUTURE_X');
    expect(chip.textContent).toContain('unknown');
  });
});

// ===========================================================================
// E1 reference integrity — broken/retired cross-references surfaced
// honestly (employee → retired department case).
// ===========================================================================

describe('E1 — reference integrity surfacing (broken/retired cross-references — § 2.4.8)', () => {
  it('an employee → retired department surfaces the `<RetiredReferenceBadge>` (NEVER silently sanitized)', async () => {
    // Mock the apiClient so the EmployeeDetail's cross-ref hooks
    // resolve: the employee's `departmentId` resolves to a
    // RETIRED department.
    const retiredDept: Department = {
      id: 'dept-retired',
      code: 'DEPT-OLD',
      name: 'Legacy',
      parentId: null,
      status: 'RETIRED',
      effectivePeriod: {
        effectiveFrom: '2025-01-01',
        effectiveTo: '2025-12-31',
      },
    };
    // Build a routed fetch mock: every cross-ref hook hits the
    // same-origin proxy (`/api/erp/masterdata/{master}/{id}`).
    const fetchMock = vi.fn((url: string) => {
      const u = new URL(String(url), 'http://console.local');
      if (u.pathname.includes('/departments/')) {
        return Promise.resolve(
          new Response(JSON.stringify(retiredDept), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }),
        );
      }
      // jobGrade / costCenter / employee detail — return active
      // skeletons so the cross-ref rendering paths exist; the
      // E1 assertion is on the department leg.
      return Promise.resolve(
        new Response(
          JSON.stringify({
            id: 'x',
            code: 'X',
            name: 'X',
            status: 'ACTIVE',
            effectivePeriod: {
              effectiveFrom: '2026-01-01',
              effectiveTo: null,
            },
          }),
          {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          },
        ),
      );
    });
    vi.stubGlobal('fetch', fetchMock);

    const empWithRetiredDept: Employee = {
      ...EMPLOYED_EMP,
      departmentId: 'dept-retired',
    };
    render(
      <EmployeeDetail id={empWithRetiredDept.id} initial={empWithRetiredDept} />,
      { wrapper: wrapper() },
    );
    // The employee detail renders the `departmentId` ref.
    expect(
      screen.getByTestId('erp-employee-department-ref'),
    ).toBeInTheDocument();
    // Wait for the cross-ref hook to resolve and the
    // retired-reference badge to surface.
    const badge = await screen.findByTestId('erp-retired-reference');
    expect(badge).toBeInTheDocument();
    expect(badge.textContent).toContain('retired department');
  });
});

// ===========================================================================
// Confidential — render path never logs tokens / PII / financial /
// sensitive attrs (console spy).
// ===========================================================================

describe('confidential — the render path logs nothing sensitive (console spy; § 2.4.8)', () => {
  it('rendering an employee list does NOT emit PII through any console channel', () => {
    const logSpy = vi.spyOn(console, 'log').mockImplementation(() => {});
    const infoSpy = vi.spyOn(console, 'info').mockImplementation(() => {});
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    render(<EmployeeList initial={EMP_LIST} />, { wrapper: wrapper() });

    const all = [
      ...logSpy.mock.calls,
      ...infoSpy.mock.calls,
      ...warnSpy.mock.calls,
      ...errorSpy.mock.calls,
    ]
      .map((args) => args.map(String).join(' '))
      .join('\n');
    // PII never appears in the console.
    expect(all).not.toContain('Active Person');
    expect(all).not.toContain('Separated Person');
    expect(all).not.toContain('EMP-001');
    expect(all).not.toContain('EMP-002');

    logSpy.mockRestore();
    infoSpy.mockRestore();
    warnSpy.mockRestore();
    errorSpy.mockRestore();
  });
});

// ===========================================================================
// `<AsOfPicker>` — URL-bound (the E3 first-class control). The
// URL→query→producer pass-through chain is covered by erp-api +
// erp-proxy; here we assert the picker mounts + is keyboard-
// operable (a native <input type="date">) + has an associated
// label (WCAG AA).
// ===========================================================================

describe('<AsOfPicker> — E3 first-class control (URL-bound, WCAG AA)', () => {
  it('renders a date input + apply/clear buttons (keyboard-operable)', () => {
    render(<AsOfPicker />, { wrapper: wrapper() });
    const input = screen.getByTestId('erp-asof-input') as HTMLInputElement;
    expect(input).toBeInTheDocument();
    expect(input.type).toBe('date');
    // The input has an associated label (htmlFor / id matches).
    const label = screen.getByText('조회 기준일 (asOf)');
    expect(label.tagName).toBe('LABEL');
    // Apply + Clear buttons are keyboard-operable (native
    // <button>).
    expect(screen.getByTestId('erp-asof-submit')).toBeInTheDocument();
    expect(screen.getByTestId('erp-asof-clear')).toBeInTheDocument();
  });
});

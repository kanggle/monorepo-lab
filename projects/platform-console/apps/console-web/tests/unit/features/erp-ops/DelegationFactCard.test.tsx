import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';

/**
 * `<DelegationFactCard>` component test (TASK-PC-FE-055 + TASK-PC-FE-056 — ADR-MONO-013 §D3.1).
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
 *   - **scope column** (TASK-PC-FE-056 AC-1): REQUEST→"특정 건"+scopeRequestId,
 *     GLOBAL→"전체", scope absent→"—" no crash, unknown→verbatim.
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

// TASK-PC-FE-335 — the delegatorId/delegateId filters are EMPLOYEE SELECTORS
// now (were raw-id text inputs). One shared `GET /api/erp/masterdata/employees`
// feeds both; a default success stub below means every PRE-EXISTING test in
// this file (none of which mocked `fetch` before — they relied solely on the
// `initial` prop seeding the delegation-facts query) keeps working unchanged.
// TASK-ERP-BE-045 — that fetch is now the «선택용» picker list
// `GET /api/erp/masterdata/employees/picker` (elements carry `accountLinked`, `meta.scope`).
const PICKER_PATH = '/api/erp/masterdata/employees/picker?';
const FILTER_EMPLOYEES_PAGE = {
  data: [
    {
      id: 'emp-001',
      employeeNumber: 'E1',
      name: '김위임',
      departmentId: 'dept-1',
      departmentName: '총무부',
      status: 'ACTIVE',
      accountLinked: true,
    },
    {
      id: 'emp-002',
      employeeNumber: 'E2',
      name: '이대행',
      departmentId: 'dept-1',
      status: 'RETIRED',
      accountLinked: false,
    },
  ],
  meta: { page: 0, size: 100, totalElements: 2, scope: 'TENANT' },
};
const FILTER_EMPLOYEES_PAGE_SUBTREE = {
  ...FILTER_EMPLOYEES_PAGE,
  meta: { ...FILTER_EMPLOYEES_PAGE.meta, scope: 'SUBTREE' },
};

beforeEach(() => {
  vi.unstubAllGlobals();
  vi.stubGlobal(
    'fetch',
    vi.fn((url: string) => {
      if (String(url).startsWith(PICKER_PATH)) {
        return Promise.resolve(jsonResponse(FILTER_EMPLOYEES_PAGE));
      }
      return Promise.resolve(errorResponse('MASTERDATA_NOT_FOUND', 404));
    }),
  );
});

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
 *  scenario: validFrom absent, validTo absent, reason absent, scope ABSENT). */
const ALL_ABSENT_FACT: DelegationFactListResponse['data'][number] = {
  grantId: 'dgr-003',
  status: 'REVOKED',
  delegatorId: 'emp-A-003',
  delegateId: 'emp-D-003',
  // validFrom, validTo, reason, revokedAt, scope all ABSENT
};

/** REQUEST-scoped fact: delegate acts for one specific approval request. */
const REQUEST_SCOPED_FACT: DelegationFactListResponse['data'][number] = {
  grantId: 'dgr-004',
  status: 'ACTIVE',
  delegatorId: 'emp-A-004',
  delegateId: 'emp-D-004',
  validFrom: '2026-06-01T00:00:00Z',
  scope: 'REQUEST',
  scopeRequestId: 'appr-77',
};

/** GLOBAL-scoped fact: delegate acts for ALL approvals of delegator. */
const GLOBAL_SCOPED_FACT: DelegationFactListResponse['data'][number] = {
  grantId: 'dgr-005',
  status: 'ACTIVE',
  delegatorId: 'emp-A-005',
  delegateId: 'emp-D-005',
  validFrom: '2026-06-01T00:00:00Z',
  scope: 'GLOBAL',
};

/** Fact with an unknown/future scope value — free-string tolerance. */
const UNKNOWN_SCOPE_FACT: DelegationFactListResponse['data'][number] = {
  grantId: 'dgr-006',
  status: 'ACTIVE',
  delegatorId: 'emp-A-006',
  delegateId: 'emp-D-006',
  validFrom: '2026-06-01T00:00:00Z',
  scope: 'FUTURE_SCOPE',
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
    expect(revokedAtCell.textContent).toContain('2026. 5. 20. 19:00:00');
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

// ---------------------------------------------------------------------------
// Tests — scope column (TASK-PC-FE-056 AC-1)
// ---------------------------------------------------------------------------

describe('<DelegationFactCard> — scope column (AC-1 PC-FE-056)', () => {
  it('REQUEST scope fact shows "특정 건" and the scopeRequestId', () => {
    const initial = makeListResp([REQUEST_SCOPED_FACT]);
    render(<DelegationFactCard initial={initial} />, { wrapper: wrapper() });

    const scopeCell = screen.getByTestId('delegation-fact-scope-0');
    expect(scopeCell.textContent).toContain('특정 건');
    expect(scopeCell.textContent).toContain('appr-77');
  });

  it('GLOBAL scope fact shows "전체"', () => {
    const initial = makeListResp([GLOBAL_SCOPED_FACT]);
    render(<DelegationFactCard initial={initial} />, { wrapper: wrapper() });

    const scopeCell = screen.getByTestId('delegation-fact-scope-0');
    expect(scopeCell.textContent).toContain('전체');
    expect(scopeCell.textContent).not.toContain('특정 건');
  });

  it('scope absent fact shows "—" and does not crash', () => {
    const initial = makeListResp([ALL_ABSENT_FACT]);
    expect(() =>
      render(<DelegationFactCard initial={initial} />, { wrapper: wrapper() }),
    ).not.toThrow();

    const scopeCell = screen.getByTestId('delegation-fact-scope-0');
    expect(scopeCell.textContent).toBe('—');
  });

  it('unknown (future) scope value is rendered verbatim', () => {
    const initial = makeListResp([UNKNOWN_SCOPE_FACT]);
    render(<DelegationFactCard initial={initial} />, { wrapper: wrapper() });

    const scopeCell = screen.getByTestId('delegation-fact-scope-0');
    expect(scopeCell.textContent).toContain('FUTURE_SCOPE');
  });

  it('GLOBAL fact does NOT show scopeRequestId (not present)', () => {
    const initial = makeListResp([GLOBAL_SCOPED_FACT]);
    render(<DelegationFactCard initial={initial} />, { wrapper: wrapper() });

    const scopeCell = screen.getByTestId('delegation-fact-scope-0');
    // scopeRequestId absent for GLOBAL → no id displayed
    expect(scopeCell.textContent).not.toContain('appr-');
  });

  it('renders scope column for all rows in a mixed list', () => {
    const initial = makeListResp([REQUEST_SCOPED_FACT, GLOBAL_SCOPED_FACT, ALL_ABSENT_FACT]);
    render(<DelegationFactCard initial={initial} />, { wrapper: wrapper() });

    expect(screen.getByTestId('delegation-fact-scope-0').textContent).toContain('특정 건');
    expect(screen.getByTestId('delegation-fact-scope-1').textContent).toContain('전체');
    expect(screen.getByTestId('delegation-fact-scope-2').textContent).toBe('—');
  });
});

// ---------------------------------------------------------------------------
// Tests — delegatorId/delegateId filter selectors (TASK-PC-FE-335).
// ---------------------------------------------------------------------------

describe('<DelegationFactCard> — filter selectors (TASK-PC-FE-335)', () => {
  it('AC-1 — the two filters render as employee selectors (blank = "전체 …")', async () => {
    const initial = makeListResp([ACTIVE_FACT]);
    render(<DelegationFactCard initial={initial} />, { wrapper: wrapper() });
    await waitFor(() =>
      expect(screen.getByTestId('delegation-fact-filter-delegatorId')).toBeEnabled(),
    );
    const delegatorSel = screen.getByTestId(
      'delegation-fact-filter-delegatorId',
    ) as HTMLSelectElement;
    const delegateSel = screen.getByTestId(
      'delegation-fact-filter-delegateId',
    ) as HTMLSelectElement;
    expect(delegatorSel.tagName).toBe('SELECT');
    expect(delegateSel.tagName).toBe('SELECT');
    expect(delegatorSel.options[0].textContent).toBe('전체 위임자');
    expect(delegateSel.options[0].textContent).toBe('전체 대행자');
  });

  it('AC-5 — no filter chosen (default) → the delegatorId/delegateId params are OMITTED from the fetched query', async () => {
    const fetchMock = vi.fn((url: string) => {
      if (String(url).startsWith(PICKER_PATH)) {
        return Promise.resolve(jsonResponse(FILTER_EMPLOYEES_PAGE));
      }
      if (String(url).startsWith('/api/erp/read-model/delegations')) {
        return Promise.resolve(
          jsonResponse({ data: [], meta: { page: 0, size: 20, totalElements: 0 } }),
        );
      }
      return Promise.resolve(errorResponse('MASTERDATA_NOT_FOUND', 404));
    });
    vi.stubGlobal('fetch', fetchMock);
    // No `initial` — the facts query is never "seeded", so it always fetches
    // on mount with the default (no-filter) query.
    render(<DelegationFactCard />, { wrapper: wrapper() });
    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some((c) =>
          String(c[0]).startsWith('/api/erp/read-model/delegations'),
        ),
      ).toBe(true),
    );
    const call = fetchMock.mock.calls.find((c) =>
      String(c[0]).startsWith('/api/erp/read-model/delegations'),
    )!;
    const url = String(call[0]);
    expect(url).not.toContain('delegatorId=');
    expect(url).not.toContain('delegateId=');
  });

  it('AC-1/AC-5 — picking a delegatorId option and applying sends that CHOSEN id as `delegatorId` on the query', async () => {
    const fetchMock = vi.fn((url: string) => {
      if (String(url).startsWith(PICKER_PATH)) {
        return Promise.resolve(jsonResponse(FILTER_EMPLOYEES_PAGE));
      }
      if (String(url).startsWith('/api/erp/read-model/delegations')) {
        return Promise.resolve(
          jsonResponse({ data: [], meta: { page: 0, size: 20, totalElements: 0 } }),
        );
      }
      return Promise.resolve(errorResponse('MASTERDATA_NOT_FOUND', 404));
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<DelegationFactCard />, { wrapper: wrapper() });
    await waitFor(() =>
      expect(screen.getByTestId('delegation-fact-filter-delegatorId')).toBeEnabled(),
    );
    await user.selectOptions(
      screen.getByTestId('delegation-fact-filter-delegatorId'),
      'emp-001',
    );
    await user.click(screen.getByTestId('delegation-fact-filter-apply'));
    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some(
          (c) =>
            String(c[0]).startsWith('/api/erp/read-model/delegations') &&
            String(c[0]).includes('delegatorId=emp-001'),
        ),
      ).toBe(true),
    );
  });

  it('AC-3 — employee list unreadable → both filters fall back to id inputs (same testids) and say so', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(errorResponse('DATA_SCOPE_FORBIDDEN', 403)),
    );
    const initial = makeListResp([ACTIVE_FACT]);
    render(<DelegationFactCard initial={initial} />, { wrapper: wrapper() });
    await screen.findByTestId('delegation-fact-filter-employees-fallback');
    expect(screen.getByTestId('delegation-fact-filter-delegatorId').tagName).toBe('INPUT');
    expect(screen.getByTestId('delegation-fact-filter-delegateId').tagName).toBe('INPUT');
    // TASK-PC-FE-349 — 403 wording (TASK-ERP-BE-045 — names both permissions; a 결재 읽기-only
    // operator lands here: the picker does not open to ERP_APPROVAL_READ).
    expect(screen.getByTestId('delegation-fact-filter-employees-fallback')).toHaveTextContent(
      '기준정보 조회 · 결재 변경 권한이 없어 직원 목록을 볼 수 없습니다. 위임자/대행자 직원 ID 를 직접 입력하세요.',
    );
    expect(screen.queryByTestId('delegation-fact-filter-delegatorId-manual-toggle')).toBeNull();
    expect(screen.queryByTestId('delegation-fact-filter-delegateId-manual-toggle')).toBeNull();
  });

  it('🔴 TASK-ERP-BE-045 — both filters are fed by `/employees/picker` (never the master `/employees` list)', async () => {
    const fetchMock = vi.fn((url: string) => {
      if (String(url).startsWith(PICKER_PATH)) {
        return Promise.resolve(jsonResponse(FILTER_EMPLOYEES_PAGE));
      }
      return Promise.resolve(errorResponse('MASTERDATA_NOT_FOUND', 404));
    });
    vi.stubGlobal('fetch', fetchMock);
    render(<DelegationFactCard initial={makeListResp([ACTIVE_FACT])} />, { wrapper: wrapper() });
    await waitFor(() =>
      expect(screen.getByTestId('delegation-fact-filter-delegatorId')).toBeEnabled(),
    );
    const urls = fetchMock.mock.calls.map((c) => String(c[0]));
    expect(urls.some((u) => u.startsWith(PICKER_PATH))).toBe(true);
    expect(urls.some((u) => u.startsWith('/api/erp/masterdata/employees?'))).toBe(false);
    const delegateSel = screen.getByTestId('delegation-fact-filter-delegateId') as HTMLSelectElement;
    expect(Array.from(delegateSel.options).map((o) => o.value)).toEqual(['', 'emp-001', 'emp-002']);
    // `accountLinked:false` → marked; retired → marked (both rules from the shared label).
    const opt002 = Array.from(delegateSel.options).find((o) => o.value === 'emp-002')!;
    expect(opt002.textContent).toContain('연결된 계정 없음');
    expect(opt002.textContent).toContain('폐기된 직원');
  });

  it('TENANT scope + not truncated → no scoped notice, no toggles', async () => {
    render(<DelegationFactCard initial={makeListResp([ACTIVE_FACT])} />, { wrapper: wrapper() });
    await waitFor(() =>
      expect(screen.getByTestId('delegation-fact-filter-delegatorId')).toBeEnabled(),
    );
    expect(screen.queryByTestId('delegation-fact-filter-employees-scoped')).toBeNull();
    expect(screen.queryByTestId('delegation-fact-filter-delegatorId-manual-toggle')).toBeNull();
    expect(screen.queryByTestId('delegation-fact-filter-delegateId-manual-toggle')).toBeNull();
  });

  it('🔴 SUBTREE scope → one scoped notice + a toggle per filter; each swaps independently (same testid)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string) => {
        if (String(url).startsWith(PICKER_PATH)) {
          return Promise.resolve(jsonResponse(FILTER_EMPLOYEES_PAGE_SUBTREE));
        }
        return Promise.resolve(errorResponse('MASTERDATA_NOT_FOUND', 404));
      }),
    );
    const user = userEvent.setup();
    render(<DelegationFactCard initial={makeListResp([ACTIVE_FACT])} />, { wrapper: wrapper() });
    await waitFor(() =>
      expect(screen.getByTestId('delegation-fact-filter-delegatorId')).toBeEnabled(),
    );
    expect(screen.getByTestId('delegation-fact-filter-employees-scoped')).toHaveTextContent(
      '내 부서 범위의 직원만 표시합니다. 목록에 없는 직원은 «ID 직접 입력» 을 누르세요.',
    );
    await user.click(screen.getByTestId('delegation-fact-filter-delegatorId-manual-toggle'));
    expect(screen.getByTestId('delegation-fact-filter-delegatorId').tagName).toBe('INPUT');
    expect(screen.getByTestId('delegation-fact-filter-delegateId').tagName).toBe('SELECT');
    expect(
      screen.getByTestId('delegation-fact-filter-delegatorId-manual-toggle'),
    ).toHaveTextContent('목록에서 고르기');
    await user.click(screen.getByTestId('delegation-fact-filter-delegatorId-manual-toggle'));
    expect(screen.getByTestId('delegation-fact-filter-delegatorId').tagName).toBe('SELECT');
  });

  it('retired employees stay selectable in the filters (historical filter, not a write)', async () => {
    const initial = makeListResp([ACTIVE_FACT]);
    render(<DelegationFactCard initial={initial} />, { wrapper: wrapper() });
    await waitFor(() =>
      expect(screen.getByTestId('delegation-fact-filter-delegatorId')).toBeEnabled(),
    );
    const sel = screen.getByTestId(
      'delegation-fact-filter-delegatorId',
    ) as HTMLSelectElement;
    const retiredOpt = Array.from(sel.options).find((o) => o.value === 'emp-002')!;
    expect(retiredOpt.textContent).toContain('폐기된 직원');
    expect(retiredOpt.disabled).toBe(false);
  });
});

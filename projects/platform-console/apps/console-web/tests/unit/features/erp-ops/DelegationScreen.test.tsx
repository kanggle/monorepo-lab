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
    render(<DelegationScreen canWrite />, { wrapper: wrapper() });
    expect(screen.getByTestId('delegation-screen')).toBeInTheDocument();
    expect(screen.getByTestId('delegation-list-delegator')).toBeInTheDocument();
    expect(screen.getByTestId('delegation-list-delegate')).toBeInTheDocument();
  });

  it('delegator list shows active grant row + revoke button', async () => {
    vi.stubGlobal('fetch', makeFetchMock());
    render(<DelegationScreen canWrite />, { wrapper: wrapper() });
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
    render(<DelegationScreen canWrite />, { wrapper: wrapper() });
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
    render(<DelegationScreen canWrite />, { wrapper: wrapper() });
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
    render(<DelegationScreen canWrite />, { wrapper: wrapper() });
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
    render(<DelegationScreen canWrite />, { wrapper: wrapper() });
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
    render(<DelegationScreen canWrite />, { wrapper: wrapper() });
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
    render(<DelegationScreen canWrite />, { wrapper: wrapper() });
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
    render(<DelegationScreen canWrite />, { wrapper: wrapper() });
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

// TASK-PC-FE-335 AC-1/AC-3/AC-4 — 대결자 is an EMPLOYEE SELECTOR now (it was a
// raw-id text input). Same testid `delegation-create-delegateId`, but a
// <select>; a RETIRED employee is listed but its <option> is disabled
// (delegateId must be ACTIVE — 422 `delegate_unresolved`).
// TASK-ERP-BE-045 — fed by the «선택용» picker list `GET /api/erp/masterdata/employees/picker`
// (elements carry `accountLinked`; `meta.scope` says whether the list was narrowed).
const PICKER_PATH = '/api/erp/masterdata/employees/picker?';
const EMP_ACTIVE_DELEGATE = {
  id: 'emp-delegate',
  employeeNumber: 'E-D1',
  name: '김대결',
  departmentId: 'dept-1',
  departmentName: '총무부',
  status: 'ACTIVE',
  accountLinked: true,
};
const EMP_RETIRED_DELEGATE = {
  id: 'emp-retired',
  employeeNumber: 'E-D2',
  name: '이퇴사',
  departmentId: 'dept-1',
  status: 'RETIRED',
  accountLinked: true,
};
const EMP_UNLINKED_DELEGATE = {
  id: 'emp-unlinked',
  employeeNumber: 'E-D3',
  name: '박미연결',
  departmentId: 'dept-1',
  status: 'ACTIVE',
  accountLinked: false,
};
const DELEGATE_EMPLOYEES_PAGE = {
  data: [EMP_ACTIVE_DELEGATE, EMP_RETIRED_DELEGATE, EMP_UNLINKED_DELEGATE],
  meta: { page: 0, size: 100, totalElements: 3, scope: 'TENANT' },
};
const DELEGATE_EMPLOYEES_PAGE_SUBTREE = {
  ...DELEGATE_EMPLOYEES_PAGE,
  meta: { ...DELEGATE_EMPLOYEES_PAGE.meta, scope: 'SUBTREE' },
};

/** fetch mock: employee list for the delegate selector + the delegation
 *  list/create endpoints — mirrors `ApprovalScreen.test.tsx`'s
 *  `createDialogFetch()` shape. */
function makeCreateDialogFetchMock({
  delegatorList = DELEGATOR_LIST,
  delegateList = DELEGATE_LIST,
  employeesOk = true,
  employeesPage = DELEGATE_EMPLOYEES_PAGE,
  onPost,
}: {
  delegatorList?: typeof DELEGATOR_LIST;
  delegateList?: typeof DELEGATE_LIST;
  employeesOk?: boolean;
  employeesPage?: unknown;
  onPost?: () => Response;
} = {}) {
  return vi.fn((url: string, init?: RequestInit) => {
    if (String(url).startsWith(PICKER_PATH)) {
      return Promise.resolve(
        employeesOk
          ? jsonResponse(employeesPage)
          : errorResponse('PERMISSION_DENIED', 403),
      );
    }
    // the master LIST is NOT what the delegate picker reads — answer it like an
    // approval-only operator would get it (403), so a regression lands on the fallback.
    if (String(url).startsWith('/api/erp/masterdata/employees')) {
      return Promise.resolve(errorResponse('PERMISSION_DENIED', 403));
    }
    if ((init as RequestInit)?.method === 'POST') {
      return Promise.resolve(onPost ? onPost() : jsonResponse(ACTIVE_GRANT_DELEGATOR, 201));
    }
    if (String(url).includes('role=DELEGATE') && !String(url).includes('role=DELEGATOR')) {
      return Promise.resolve(jsonResponse(delegateList));
    }
    return Promise.resolve(jsonResponse(delegatorList));
  });
}

async function openCreateWithEmployees(user: ReturnType<typeof userEvent.setup>) {
  render(<DelegationScreen canWrite />, { wrapper: wrapper() });
  await user.click(screen.getByTestId('delegation-create'));
  await waitFor(() =>
    expect(screen.getByTestId('delegation-create-delegateId')).toBeEnabled(),
  );
  expect(screen.getByTestId('delegation-create-delegateId').tagName).toBe('SELECT');
}

describe('DelegationScreen — create dialog', () => {
  it('create button opens the dialog; submit is disabled until required fields filled', async () => {
    vi.stubGlobal('fetch', makeCreateDialogFetchMock());
    const user = userEvent.setup();
    await openCreateWithEmployees(user);
    expect(screen.getByTestId('delegation-create-dialog')).toBeInTheDocument();
    expect(screen.getByTestId('delegation-create-submit')).toBeDisabled();
    // Pick delegateId.
    await user.selectOptions(
      screen.getByTestId('delegation-create-delegateId'),
      'emp-delegate',
    );
    // Still disabled (no validFrom).
    expect(screen.getByTestId('delegation-create-submit')).toBeDisabled();
    // Fill validFrom.
    await user.type(screen.getByTestId('delegation-create-validFrom'), '2026-06-10');
    expect(screen.getByTestId('delegation-create-submit')).toBeEnabled();
  });

  it('AC-1 — submit sends the CHOSEN delegateId + validFrom + idempotencyKey to /api/erp/approval/delegations; closes on success', async () => {
    const delegatorList = {
      data: [ACTIVE_GRANT_DELEGATOR],
      meta: { page: 0, size: 20, totalElements: 1 },
    };
    const fetchMock = makeCreateDialogFetchMock({ delegatorList, delegateList: EMPTY_LIST });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    await openCreateWithEmployees(user);
    await user.selectOptions(
      screen.getByTestId('delegation-create-delegateId'),
      'emp-delegate',
    );
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
    const fetchMock = makeCreateDialogFetchMock({
      onPost: () => errorResponse('DELEGATION_INVALID', 422),
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    await openCreateWithEmployees(user);
    await user.selectOptions(
      screen.getByTestId('delegation-create-delegateId'),
      'emp-delegate',
    );
    await user.type(screen.getByTestId('delegation-create-validFrom'), '2026-06-10');
    await user.click(screen.getByTestId('delegation-create-submit'));
    const errEl = await screen.findByTestId('delegation-error');
    expect(errEl.textContent).toContain('자기 위임');
    // Dialog still mounted (no crash).
    expect(screen.getByTestId('delegation-create-dialog')).toBeInTheDocument();
  });

  it('🔴 AC-4 — a RETIRED employee is listed in the delegate selector but its option is disabled', async () => {
    vi.stubGlobal('fetch', makeCreateDialogFetchMock());
    const user = userEvent.setup();
    await openCreateWithEmployees(user);
    const select = screen.getByTestId(
      'delegation-create-delegateId',
    ) as HTMLSelectElement;
    const opt = (v: string) =>
      Array.from(select.options).find((o) => o.value === v)!;
    expect(opt('emp-retired').textContent).toContain('폐기된 직원');
    expect(opt('emp-retired').disabled).toBe(true);
    expect(opt('emp-delegate').disabled).toBe(false);
  });

  it('AC-3 — employee list unreadable → the delegate row falls back to an id input (same testid) and the dialog still submits', async () => {
    const fetchMock = makeCreateDialogFetchMock({ employeesOk: false });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<DelegationScreen canWrite />, { wrapper: wrapper() });
    await user.click(screen.getByTestId('delegation-create'));
    await screen.findByTestId('delegation-create-delegateId-fallback');
    expect(screen.getByTestId('delegation-create-delegateId').tagName).toBe('INPUT');
    // TASK-PC-FE-349 — the mock answers 403: permission wording (TASK-ERP-BE-045 — names both
    // 기준정보 조회 and 결재 변경, the picker's two openers).
    expect(screen.getByTestId('delegation-create-delegateId-fallback')).toHaveTextContent(
      '기준정보 조회 · 결재 변경 권한이 없어 직원 목록을 볼 수 없습니다. 대결자 직원 ID 를 직접 입력하세요.',
    );
    expect(screen.queryByTestId('delegation-create-delegateId-manual-toggle')).toBeNull();
    await user.type(screen.getByTestId('delegation-create-delegateId'), 'emp-manual');
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
    const createCall = fetchMock.mock.calls.find(
      (c) =>
        String(c[0]).endsWith('/delegations') &&
        (c[1] as RequestInit).method === 'POST',
    )!;
    const body = JSON.parse((createCall[1] as RequestInit).body as string);
    expect(body.delegateId).toBe('emp-manual');
  });
});

// ===========================================================================
// TASK-ERP-BE-045 — the delegate picker reads `/employees/picker`; a delegate normally sits
// outside the operator's subtree, so a scoped list offers «ID 직접 입력».
// ===========================================================================

describe('DelegationScreen — delegate picker reads the picker list (TASK-ERP-BE-045)', () => {
  it('🔴 the delegate selector is fed by `/employees/picker` (the master `/employees` list is never asked)', async () => {
    const fetchMock = makeCreateDialogFetchMock();
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    await openCreateWithEmployees(user);
    const urls = fetchMock.mock.calls.map((c) => String(c[0]));
    expect(urls.some((u) => u.startsWith(PICKER_PATH))).toBe(true);
    expect(urls.some((u) => u.startsWith('/api/erp/masterdata/employees?'))).toBe(false);
    const select = screen.getByTestId('delegation-create-delegateId') as HTMLSelectElement;
    expect(Array.from(select.options).map((o) => o.value)).toEqual([
      '',
      'emp-delegate',
      'emp-retired',
      'emp-unlinked',
    ]);
  });

  it('`accountLinked:false` → the option is marked «연결된 계정 없음» (still selectable); a linked one is not', async () => {
    vi.stubGlobal('fetch', makeCreateDialogFetchMock());
    const user = userEvent.setup();
    await openCreateWithEmployees(user);
    const select = screen.getByTestId('delegation-create-delegateId') as HTMLSelectElement;
    const opt = (v: string) => Array.from(select.options).find((o) => o.value === v)!;
    expect(opt('emp-unlinked').textContent).toContain('연결된 계정 없음');
    expect(opt('emp-unlinked').disabled).toBe(false);
    expect(opt('emp-delegate').textContent).toBe('E-D1 · 김대결');
  });

  it('TENANT scope + not truncated → no scoped notice and no toggle', async () => {
    vi.stubGlobal('fetch', makeCreateDialogFetchMock());
    const user = userEvent.setup();
    await openCreateWithEmployees(user);
    expect(screen.queryByTestId('delegation-create-delegateId-scoped')).toBeNull();
    expect(screen.queryByTestId('delegation-create-delegateId-manual-toggle')).toBeNull();
  });

  it('🔴 SUBTREE scope → scoped notice + toggle; the toggle swaps to an id input (same testid) and a typed out-of-subtree delegate is submitted', async () => {
    const fetchMock = makeCreateDialogFetchMock({ employeesPage: DELEGATE_EMPLOYEES_PAGE_SUBTREE });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    await openCreateWithEmployees(user);
    expect(screen.getByTestId('delegation-create-delegateId-scoped')).toHaveTextContent(
      '내 부서 범위의 직원만 표시합니다. 목록에 없는 직원은 «ID 직접 입력» 을 누르세요.',
    );
    const toggle = screen.getByTestId('delegation-create-delegateId-manual-toggle');
    expect(toggle).toHaveTextContent('ID 직접 입력');
    await user.click(toggle);
    expect(screen.getByTestId('delegation-create-delegateId').tagName).toBe('INPUT');
    expect(screen.getByTestId('delegation-create-delegateId-manual-toggle')).toHaveTextContent(
      '목록에서 고르기',
    );
    await user.type(screen.getByTestId('delegation-create-delegateId'), 'emp-outside');
    await user.type(screen.getByTestId('delegation-create-validFrom'), '2026-06-10');
    await user.click(screen.getByTestId('delegation-create-submit'));
    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some(
          (c) => String(c[0]).endsWith('/delegations') && (c[1] as RequestInit).method === 'POST',
        ),
      ).toBe(true),
    );
    const createCall = fetchMock.mock.calls.find(
      (c) => String(c[0]).endsWith('/delegations') && (c[1] as RequestInit).method === 'POST',
    )!;
    expect(JSON.parse((createCall[1] as RequestInit).body as string).delegateId).toBe(
      'emp-outside',
    );
  });

  it('the toggle swaps back to the select; RETIRED stays disabled there (rule unchanged)', async () => {
    vi.stubGlobal(
      'fetch',
      makeCreateDialogFetchMock({ employeesPage: DELEGATE_EMPLOYEES_PAGE_SUBTREE }),
    );
    const user = userEvent.setup();
    await openCreateWithEmployees(user);
    await user.click(screen.getByTestId('delegation-create-delegateId-manual-toggle'));
    await user.click(screen.getByTestId('delegation-create-delegateId-manual-toggle'));
    const select = screen.getByTestId('delegation-create-delegateId') as HTMLSelectElement;
    expect(select.tagName).toBe('SELECT');
    const retired = Array.from(select.options).find((o) => o.value === 'emp-retired')!;
    expect(retired.disabled).toBe(true);
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
    render(<DelegationScreen canWrite />, { wrapper: wrapper() });
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
    render(<DelegationScreen canWrite />, { wrapper: wrapper() });
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

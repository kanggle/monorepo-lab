import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { MASTER_REF_UNRESOLVED } from '@/shared/lib/master-ref-label';

/**
 * TASK-PC-FE-051 — `<ApprovalScreen>` / `<ApprovalDetail>` / the reason +
 * create dialogs. Asserts:
 *   - list (status filter) + inbox render; row → detail;
 *   - state-gated action visibility (DRAFT → submit/withdraw; SUBMITTED →
 *     approve/reject/withdraw; terminal → none);
 *   - reject / withdraw open a reason-required dialog (gated until non-blank);
 *   - history timeline renders, absent fields show "—";
 *   - the approval error codes map to inline messages (no crash);
 *   - create dialog gates required fields + POSTs with an Idempotency-Key.
 * Same-origin `/api/erp/approval/**` fetch mocked.
 *
 * TASK-PC-FE-309 — every subject/approver/submitter/actor cell now resolves
 * via `useDepartment`/`useEmployee` (see `approval-refs.tsx`) instead of
 * printing the raw id. None of the fixtures below seed a matching
 * department/employee detail response, so every resolution in THIS file
 * legitimately lands on `이름 확인 불가` (MASTER_REF_UNRESOLVED) — honest, not
 * a crash. The POSITIVE (resolved-name) case is covered in the shared
 * regression guard `tests/unit/erp-master-ref-names.test.tsx`, which mocks
 * `useDepartment`/`useEmployee` directly (this file's concern stays the
 * approval state machine, not master-ref resolution).
 */

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: vi.fn() }),
  usePathname: () => '/erp',
  useSearchParams: () => new URLSearchParams(),
}));

import { ApprovalScreen, ApprovalDetail } from '@/features/erp-ops';
import type {
  ApprovalListResponse,
  ApprovalRequest,
} from '@/features/erp-ops';
import {
  APPROVAL_INBOX_EMPTY_MESSAGE,
  APPROVAL_INBOX_UNLINKED_MESSAGE,
} from '@/features/erp-ops/components/ApprovalScreen';
import { approvalErrorMessage } from '@/features/erp-ops/components/approval-error';
import { ApiError } from '@/shared/api/errors';

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

const LIST: ApprovalListResponse = {
  data: [
    {
      id: 'appr-1',
      status: 'SUBMITTED',
      subjectType: 'DEPARTMENT',
      subjectId: 'dept-1',
      title: '조직개편 결재',
      approverId: 'emp-a',
      submitterId: 'emp-s',
      createdAt: '2026-06-05T00:00:00Z',
      submittedAt: '2026-06-05T01:00:00Z',
    },
  ],
  meta: { page: 0, size: 20, totalElements: 1, timestamp: 'x' },
};
const INBOX: ApprovalListResponse = {
  data: [
    {
      id: 'appr-1',
      status: 'SUBMITTED',
      subjectType: 'DEPARTMENT',
      subjectId: 'dept-1',
      title: '조직개편 결재',
      approverId: 'emp-a',
      submitterId: 'emp-s',
      createdAt: '2026-06-05T00:00:00Z',
      submittedAt: '2026-06-05T01:00:00Z',
    },
  ],
  meta: { page: 0, size: 20, totalElements: 1, timestamp: 'x' },
};

const DRAFT: ApprovalRequest = {
  id: 'appr-draft',
  status: 'DRAFT',
  subjectType: 'DEPARTMENT',
  subjectId: 'dept-1',
  title: 'DRAFT 결재',
  approverId: 'emp-a',
  submitterId: 'emp-s',
  history: [],
  createdAt: '2026-06-05T00:00:00Z',
  // submittedAt / finalizedAt ABSENT (NON_NULL).
};
const SUBMITTED: ApprovalRequest = {
  id: 'appr-sub',
  status: 'SUBMITTED',
  subjectType: 'DEPARTMENT',
  subjectId: 'dept-1',
  title: 'SUBMITTED 결재',
  approverId: 'emp-a',
  submitterId: 'emp-s',
  history: [
    { transition: 'SUBMITTED', actor: 'emp-s', at: '2026-06-05T01:00:00Z' },
  ],
  createdAt: '2026-06-05T00:00:00Z',
  submittedAt: '2026-06-05T01:00:00Z',
  // finalizedAt ABSENT.
};
const APPROVED: ApprovalRequest = {
  id: 'appr-app',
  status: 'APPROVED',
  subjectType: 'DEPARTMENT',
  subjectId: 'dept-1',
  title: 'APPROVED 결재',
  approverId: 'emp-a',
  submitterId: 'emp-s',
  history: [
    { transition: 'SUBMITTED', actor: 'emp-s', at: '2026-06-05T01:00:00Z' },
    { transition: 'APPROVED', actor: 'emp-a', at: '2026-06-05T02:00:00Z', reason: '승인' },
  ],
  createdAt: '2026-06-05T00:00:00Z',
  submittedAt: '2026-06-05T01:00:00Z',
  finalizedAt: '2026-06-05T02:00:00Z',
};

beforeEach(() => {
  vi.unstubAllGlobals();
  // TASK-PC-FE-309 — safe default so the new per-cell useDepartment/useEmployee
  // resolution never hits a REAL network call in a test that only seeds
  // `initialRequests`/`initialInbox` and never stubs fetch itself (e.g. the
  // plain list-rendering tests below). Tests that need a specific fetch
  // behaviour call `vi.stubGlobal('fetch', ...)` again and fully replace this.
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(errorResponse('MASTERDATA_NOT_FOUND', 404)),
  );
});

// ===========================================================================
// screen — list + inbox.
// ===========================================================================

describe('ApprovalScreen — list + inbox', () => {
  it('renders the requests list + inbox from the seed', () => {
    render(<ApprovalScreen initialRequests={LIST} initialInbox={INBOX} />, {
      wrapper: wrapper(),
    });
    expect(screen.getByTestId('approval-list-table')).toBeInTheDocument();
    expect(screen.getByTestId('approval-row-0')).toBeInTheDocument();
    expect(screen.getByTestId('approval-inbox-list')).toBeInTheDocument();
    expect(screen.getByTestId('approval-inbox-item-appr-1')).toBeInTheDocument();
  });

  it('empty inbox (linked caller) renders the empty notice (no crash)', () => {
    render(
      <ApprovalScreen
        initialRequests={LIST}
        initialInbox={{
          data: [],
          meta: { page: 0, size: 20, totalElements: 0, actorEmployeeId: 'emp-me' },
        }}
      />,
      { wrapper: wrapper() },
    );
    expect(screen.getByTestId('approval-inbox-empty')).toBeInTheDocument();
  });

  // TASK-PC-FE-318 AC-3 — `meta.actorEmployeeId` ABSENT ↔ present-and-empty must
  // read DIFFERENTLY (Failure Scenario 3: «not linked» folded into «nothing to do»).
  it('AC-3 — actorEmployeeId ABSENT → «연결되지 않아 비어 있습니다», not the plain empty notice', () => {
    render(
      <ApprovalScreen
        initialRequests={LIST}
        initialInbox={{ data: [], meta: { page: 0, size: 20, totalElements: 0 } }}
      />,
      { wrapper: wrapper() },
    );
    const unlinked = screen.getByTestId('approval-inbox-unlinked');
    expect(unlinked.textContent).toBe(APPROVAL_INBOX_UNLINKED_MESSAGE);
    expect(unlinked.textContent).toContain('직원과 연결되지 않아');
    expect(screen.queryByTestId('approval-inbox-empty')).not.toBeInTheDocument();
  });

  it('AC-3 — actorEmployeeId present + 0 rows → «처리할 결재가 없습니다» (a different sentence)', () => {
    render(
      <ApprovalScreen
        initialRequests={LIST}
        initialInbox={{
          data: [],
          meta: { page: 0, size: 20, totalElements: 0, actorEmployeeId: 'emp-me' },
        }}
      />,
      { wrapper: wrapper() },
    );
    expect(screen.getByTestId('approval-inbox-empty').textContent).toBe(
      APPROVAL_INBOX_EMPTY_MESSAGE,
    );
    expect(screen.queryByTestId('approval-inbox-unlinked')).not.toBeInTheDocument();
    expect(APPROVAL_INBOX_EMPTY_MESSAGE).not.toBe(APPROVAL_INBOX_UNLINKED_MESSAGE);
  });

  it('the status filter is present with all 6 statuses (incl. IN_REVIEW)', () => {
    render(<ApprovalScreen initialRequests={LIST} initialInbox={INBOX} />, {
      wrapper: wrapper(),
    });
    const sel = screen.getByTestId('approval-status-filter');
    expect(sel.tagName).toBe('SELECT');
    expect(screen.getByRole('option', { name: '승인됨' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: '반려됨' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: '검토중' })).toBeInTheDocument();
  });
});

// ===========================================================================
// detail — state-gated actions + history + absent fields.
// ===========================================================================

describe('ApprovalDetail — state-gated actions', () => {
  it('DRAFT → submit + withdraw actions only (no approve/reject)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ data: DRAFT })));
    render(<ApprovalDetail id="appr-draft" onClose={() => {}} />, {
      wrapper: wrapper(),
    });
    await screen.findByTestId('approval-detail');
    await waitFor(() =>
      expect(screen.getByTestId('approval-action-submit')).toBeInTheDocument(),
    );
    expect(screen.getByTestId('approval-action-withdraw')).toBeInTheDocument();
    expect(screen.queryByTestId('approval-action-approve')).not.toBeInTheDocument();
    expect(screen.queryByTestId('approval-action-reject')).not.toBeInTheDocument();
  });

  it('SUBMITTED → approve + reject + withdraw (no submit)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ data: SUBMITTED })));
    render(<ApprovalDetail id="appr-sub" onClose={() => {}} />, {
      wrapper: wrapper(),
    });
    await waitFor(() =>
      expect(screen.getByTestId('approval-action-approve')).toBeInTheDocument(),
    );
    expect(screen.getByTestId('approval-action-reject')).toBeInTheDocument();
    expect(screen.getByTestId('approval-action-withdraw')).toBeInTheDocument();
    expect(screen.queryByTestId('approval-action-submit')).not.toBeInTheDocument();
  });

  it('terminal (APPROVED) → NO transition actions; "완료" notice; history + reason render', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ data: APPROVED })));
    render(<ApprovalDetail id="appr-app" onClose={() => {}} />, {
      wrapper: wrapper(),
    });
    await waitFor(() =>
      expect(screen.getByTestId('approval-no-actions')).toBeInTheDocument(),
    );
    expect(screen.queryByTestId('approval-action-approve')).not.toBeInTheDocument();
    expect(screen.queryByTestId('approval-action-submit')).not.toBeInTheDocument();
    // history timeline + the APPROVED entry's reason.
    expect(screen.getByTestId('approval-history')).toBeInTheDocument();
    expect(screen.getByTestId('approval-history-reason-1').textContent).toContain('승인');
  });

  it('DRAFT absent submittedAt / finalizedAt render as "—" (NON_NULL)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ data: DRAFT })));
    render(<ApprovalDetail id="appr-draft" onClose={() => {}} />, {
      wrapper: wrapper(),
    });
    await waitFor(() =>
      expect(screen.getByTestId('approval-detail-submittedAt')).toBeInTheDocument(),
    );
    expect(screen.getByTestId('approval-detail-submittedAt').textContent).toBe('—');
    expect(screen.getByTestId('approval-detail-finalizedAt').textContent).toBe('—');
    expect(screen.getByTestId('approval-history-empty')).toBeInTheDocument();
  });
});

// ===========================================================================
// reason-gated reject / withdraw.
// ===========================================================================

describe('ApprovalDetail — reason-gated reject', () => {
  it('reject opens a reason dialog gated until a non-blank reason; then POSTs .../reject', async () => {
    const fetchMock = vi.fn((url: string, _init?: RequestInit) => {
      if (String(url).includes('/reject')) {
        return Promise.resolve(jsonResponse({ data: { ...SUBMITTED, status: 'REJECTED' } }));
      }
      return Promise.resolve(jsonResponse({ data: SUBMITTED }));
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<ApprovalDetail id="appr-sub" onClose={() => {}} />, {
      wrapper: wrapper(),
    });
    await waitFor(() =>
      expect(screen.getByTestId('approval-action-reject')).toBeInTheDocument(),
    );
    await user.click(screen.getByTestId('approval-action-reject'));
    const dialog = screen.getByTestId('approval-reason-dialog');
    expect(dialog).toHaveAttribute('data-transition', 'reject');
    // gated until non-blank reason.
    expect(screen.getByTestId('approval-reason-confirm')).toBeDisabled();
    await user.type(screen.getByTestId('approval-reason-input'), '근거 부족');
    expect(screen.getByTestId('approval-reason-confirm')).toBeEnabled();
    await user.click(screen.getByTestId('approval-reason-confirm'));
    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some((c) =>
          String(c[0]).includes('/api/erp/approval/requests/appr-sub/reject'),
        ),
      ).toBe(true),
    );
    const rejectCall = fetchMock.mock.calls.find((c) =>
      String(c[0]).includes('/reject'),
    )!;
    const body = JSON.parse((rejectCall[1] as RequestInit).body as string);
    expect(body.reason).toBe('근거 부족');
    expect(typeof body.idempotencyKey).toBe('string');
  });
});

// ===========================================================================
// inline error mapping — no crash.
// ===========================================================================

describe('ApprovalDetail — inline error mapping (graceful, no crash)', () => {
  it('approve 403 APPROVAL_NOT_AUTHORIZED_APPROVER → inline "결재 권한 없음" notice (no error boundary)', async () => {
    const fetchMock = vi.fn((url: string, _init?: RequestInit) => {
      if (String(url).includes('/approve')) {
        return Promise.resolve(errorResponse('APPROVAL_NOT_AUTHORIZED_APPROVER', 403));
      }
      return Promise.resolve(jsonResponse({ data: SUBMITTED }));
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<ApprovalDetail id="appr-sub" onClose={() => {}} />, {
      wrapper: wrapper(),
    });
    await waitFor(() =>
      expect(screen.getByTestId('approval-action-approve')).toBeInTheDocument(),
    );
    await user.click(screen.getByTestId('approval-action-approve'));
    const err = await screen.findByTestId('approval-action-error');
    expect(err.textContent).toContain('결재 권한 없음');
    // The detail is still mounted (no crash / no error boundary).
    expect(screen.getByTestId('approval-detail')).toBeInTheDocument();
  });

  it('detail fetch 404 → inline detail error, no crash', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(errorResponse('APPROVAL_REQUEST_NOT_FOUND', 404)),
    );
    render(<ApprovalDetail id="nope" onClose={() => {}} />, {
      wrapper: wrapper(),
    });
    const err = await screen.findByTestId('approval-detail-error');
    expect(err.textContent).toContain('찾을 수 없습니다');
  });
});

// ===========================================================================
// create dialog.
// ===========================================================================

// TASK-PC-FE-318 AC-4 — the approver route is an EMPLOYEE SELECTOR now (it was
// a raw-id text input). Same testid `approval-create-approver-${idx}`, but a
// <select> fed by `GET /api/erp/masterdata/employees`; an employee without
// `accountId` is marked «연결된 계정 없음» (not disabled).
const EMP = (id: string, num: string, name: string, accountId?: string) => ({
  id,
  employeeNumber: num,
  name,
  status: 'ACTIVE',
  employmentStatus: 'EMPLOYED',
  effectivePeriod: { effectiveFrom: '2026-01-01', effectiveTo: null },
  ...(accountId ? { accountId } : {}),
});
const EMPLOYEES_PAGE = {
  data: [
    EMP('emp-a', 'E-A', '김승인', 'acc-a'),
    EMP('emp-b', 'E-B', '이승인', 'acc-b'),
    EMP('emp-x', 'E-X', '박미연결'),
  ],
  meta: { page: 0, size: 100, totalElements: 3 },
};

/** fetch mock: employee list for the selector, DRAFT for the create POST. */
function createDialogFetch() {
  return vi.fn((url: string, init?: RequestInit) => {
    if (String(url).startsWith('/api/erp/masterdata/employees?')) {
      return Promise.resolve(jsonResponse(EMPLOYEES_PAGE));
    }
    if (init?.method === 'POST') {
      return Promise.resolve(jsonResponse({ data: DRAFT }, 201));
    }
    return Promise.resolve(errorResponse('MASTERDATA_NOT_FOUND', 404));
  });
}

async function openCreateWithEmployees(user: ReturnType<typeof userEvent.setup>) {
  render(<ApprovalScreen initialRequests={LIST} initialInbox={INBOX} />, {
    wrapper: wrapper(),
  });
  await user.click(screen.getByTestId('approval-create'));
  // wait until the selector is fed (enabled select with the employee options).
  await waitFor(() =>
    expect(screen.getByTestId('approval-create-approver-0')).toBeEnabled(),
  );
  expect(screen.getByTestId('approval-create-approver-0').tagName).toBe('SELECT');
}

describe('ApprovalScreen — create dialog', () => {
  it('single-stage: gates required fields then POSTs /requests with approverId + Idempotency-Key (legacy)', async () => {
    const fetchMock = createDialogFetch();
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    await openCreateWithEmployees(user);
    expect(screen.getByTestId('approval-create-dialog')).toBeInTheDocument();
    expect(screen.getByTestId('approval-create-submit')).toBeDisabled();
    await user.type(screen.getByTestId('approval-create-subjectId'), 'dept-1');
    await user.type(screen.getByTestId('approval-create-title'), '조직개편');
    // The first approver row is testid approval-create-approver-0 — a selector now.
    await user.selectOptions(screen.getByTestId('approval-create-approver-0'), 'emp-a');
    expect(screen.getByTestId('approval-create-submit')).toBeEnabled();
    await user.click(screen.getByTestId('approval-create-submit'));
    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some((c) =>
          String(c[0]) === '/api/erp/approval/requests',
        ),
      ).toBe(true),
    );
    const createCall = fetchMock.mock.calls.find(
      (c) => String(c[0]) === '/api/erp/approval/requests',
    )!;
    const body = JSON.parse((createCall[1] as RequestInit).body as string);
    // 1-stage → legacy approverId (backward-compat, AC-4).
    expect(body).toMatchObject({
      subjectType: 'DEPARTMENT',
      subjectId: 'dept-1',
      title: '조직개편',
      approverId: 'emp-a',
    });
    expect(body.approverIds).toBeUndefined();
    expect(typeof body.idempotencyKey).toBe('string');
  });

  it('multi-stage: adding a 2nd row and submitting sends approverIds (not approverId)', async () => {
    const fetchMock = createDialogFetch();
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    await openCreateWithEmployees(user);
    await user.type(screen.getByTestId('approval-create-subjectId'), 'dept-1');
    await user.type(screen.getByTestId('approval-create-title'), '다단계 결재');
    // Fill row 0.
    await user.selectOptions(screen.getByTestId('approval-create-approver-0'), 'emp-a');
    // Add a second stage row.
    await user.click(screen.getByTestId('approval-create-add-stage'));
    // Row 1 should now be present.
    expect(screen.getByTestId('approval-create-approver-1')).toBeInTheDocument();
    await user.selectOptions(screen.getByTestId('approval-create-approver-1'), 'emp-b');
    expect(screen.getByTestId('approval-create-submit')).toBeEnabled();
    await user.click(screen.getByTestId('approval-create-submit'));
    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some((c) =>
          String(c[0]) === '/api/erp/approval/requests',
        ),
      ).toBe(true),
    );
    const createCall = fetchMock.mock.calls.find(
      (c) => String(c[0]) === '/api/erp/approval/requests',
    )!;
    const body = JSON.parse((createCall[1] as RequestInit).body as string);
    // 2-stage → approverIds (v2.0), NOT approverId.
    expect(body.approverIds).toEqual(['emp-a', 'emp-b']);
    expect(body.approverId).toBeUndefined();
    expect(typeof body.idempotencyKey).toBe('string');
  });

  it('remove-stage button disabled when only 1 row; enabled with 2 rows', async () => {
    const user = userEvent.setup();
    render(<ApprovalScreen initialRequests={LIST} initialInbox={INBOX} />, {
      wrapper: wrapper(),
    });
    await user.click(screen.getByTestId('approval-create'));
    // 1 row: remove button disabled.
    expect(screen.getByTestId('approval-create-remove-stage-0')).toBeDisabled();
    // Add a row.
    await user.click(screen.getByTestId('approval-create-add-stage'));
    // Now both rows are removable.
    expect(screen.getByTestId('approval-create-remove-stage-0')).toBeEnabled();
    expect(screen.getByTestId('approval-create-remove-stage-1')).toBeEnabled();
    // Remove row 1.
    await user.click(screen.getByTestId('approval-create-remove-stage-1'));
    expect(screen.queryByTestId('approval-create-approver-1')).not.toBeInTheDocument();
  });

  it('🔴 AC-4 — an employee with no linked account is marked «연결된 계정 없음» in the selector (selectable, not disabled)', async () => {
    vi.stubGlobal('fetch', createDialogFetch());
    const user = userEvent.setup();
    await openCreateWithEmployees(user);
    const select = screen.getByTestId('approval-create-approver-0') as HTMLSelectElement;
    const opt = (v: string) =>
      Array.from(select.options).find((o) => o.value === v)!;
    expect(opt('emp-x').textContent).toContain('연결된 계정 없음');
    expect(opt('emp-x').disabled).toBe(false);
    expect(opt('emp-a').textContent).not.toContain('연결된 계정 없음');
    expect(opt('emp-a').textContent).toContain('E-A · 김승인');
    // picking it shows the row-level warning (and still allows the draft).
    await user.selectOptions(select, 'emp-x');
    expect(
      screen.getByTestId('approval-create-approver-unlinked-0').textContent,
    ).toContain('연결된 계정 없음');
    // the raw id is never an option label.
    expect(Array.from(select.options).some((o) => o.textContent === 'emp-x')).toBe(false);
  });

  it('employee list unreadable → the row falls back to an employee-id input (same testid) and says so', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(errorResponse('DATA_SCOPE_FORBIDDEN', 403)),
    );
    const user = userEvent.setup();
    render(<ApprovalScreen initialRequests={LIST} initialInbox={INBOX} />, {
      wrapper: wrapper(),
    });
    await user.click(screen.getByTestId('approval-create'));
    await screen.findByTestId('approval-create-approvers-fallback');
    expect(screen.getByTestId('approval-create-approver-0').tagName).toBe('INPUT');
  });
});

// ===========================================================================
// TASK-PC-FE-318 — approval v2.4 refusals: each has its own copy.
// ===========================================================================

describe('approval v2.4 refusals (TASK-PC-FE-318)', () => {
  it('🔴 AC-4 — submit 422 APPROVAL_APPROVER_UNLINKED points at the stage (details.stageIndex) in plain words', async () => {
    const fetchMock = vi.fn((url: string) => {
      if (String(url).includes('/submit')) {
        return Promise.resolve(
          new Response(
            JSON.stringify({
              code: 'APPROVAL_APPROVER_UNLINKED',
              message: 'approver has no linked account',
              details: { stageIndex: 1 },
            }),
            { status: 422, headers: { 'Content-Type': 'application/json' } },
          ),
        );
      }
      if (String(url).includes('/api/erp/approval/requests/')) {
        return Promise.resolve(jsonResponse({ data: DRAFT }));
      }
      return Promise.resolve(errorResponse('MASTERDATA_NOT_FOUND', 404));
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<ApprovalDetail id="appr-draft" onClose={() => {}} />, { wrapper: wrapper() });
    await waitFor(() =>
      expect(screen.getByTestId('approval-action-submit')).toBeInTheDocument(),
    );
    await user.click(screen.getByTestId('approval-action-submit'));
    const err = await screen.findByTestId('approval-action-error');
    expect(err.textContent).toContain('2단계 결재자에게 연결된 계정이 없어 상신할 수 없습니다');
    expect(err.textContent).not.toContain('approver has no linked account');
  });

  it('APPROVAL_ACTOR_NOT_LINKED / ROUTE_INVALID causes / 503 — pairwise-distinct copy; 503 never mentions the link', () => {
    const m = (code: string, status: number, details?: unknown) =>
      approvalErrorMessage(new ApiError(status, code, 'm', undefined, details));
    const actor = m('APPROVAL_ACTOR_NOT_LINKED', 403);
    const unlinked0 = m('APPROVAL_APPROVER_UNLINKED', 422, { stageIndex: 0 });
    const self = m('APPROVAL_ROUTE_INVALID', 422, { cause: 'self_approval' });
    const unresolved = m('APPROVAL_ROUTE_INVALID', 422, { cause: 'approver_unresolved' });
    const generic = m('APPROVAL_ROUTE_INVALID', 422);
    const outage = m('SERVICE_UNAVAILABLE', 503);
    const all = [actor, unlinked0, self, unresolved, generic, outage];
    expect(new Set(all).size).toBe(all.length);
    expect(actor).toContain('내 계정이 직원과 연결되어 있지 않아');
    expect(unlinked0).toContain('1단계 결재자');
    expect(self).toContain('자기 자신');
    expect(unresolved).toContain('재직(ACTIVE)');
    expect(outage).toContain('일시적으로 사용할 수 없습니다');
    expect(outage).not.toContain('연결');
  });
});

// ===========================================================================
// IN_REVIEW status badge.
// ===========================================================================

describe('ApprovalScreen — IN_REVIEW status badge', () => {
  it('IN_REVIEW badge shows "검토중" label in the list', () => {
    const IN_REVIEW_LIST: ApprovalListResponse = {
      data: [
        {
          id: 'appr-inreview',
          status: 'IN_REVIEW',
          subjectType: 'DEPARTMENT',
          subjectId: 'dept-1',
          title: '검토중 결재',
          approverId: 'emp-b',
          submitterId: 'emp-s',
          createdAt: '2026-06-05T00:00:00Z',
          submittedAt: '2026-06-05T01:00:00Z',
        },
      ],
      meta: { page: 0, size: 20, totalElements: 1 },
    };
    render(<ApprovalScreen initialRequests={IN_REVIEW_LIST} initialInbox={{ data: [], meta: { page: 0, size: 20, totalElements: 0 } }} />, {
      wrapper: wrapper(),
    });
    const badge = screen.getByTestId('approval-status-badge');
    expect(badge).toHaveAttribute('data-status', 'IN_REVIEW');
    expect(badge.textContent).toBe('검토중');
  });

  it('IN_REVIEW status filter option is present', () => {
    render(<ApprovalScreen initialRequests={LIST} initialInbox={INBOX} />, {
      wrapper: wrapper(),
    });
    expect(screen.getByRole('option', { name: '검토중' })).toBeInTheDocument();
  });
});

// ===========================================================================
// detail — multi-stage timeline + IN_REVIEW + history delegation.
// ===========================================================================

const MULTI_STAGE: ApprovalRequest = {
  id: 'appr-multi',
  status: 'IN_REVIEW',
  subjectType: 'DEPARTMENT',
  subjectId: 'dept-1',
  title: '다단계 결재',
  approverId: 'emp-b',
  submitterId: 'emp-s',
  history: [
    {
      transition: 'SUBMITTED',
      actor: 'emp-s',
      at: '2026-06-05T01:00:00Z',
      stage: 0,
    },
    {
      transition: 'APPROVED',
      actor: 'emp-delegate',
      at: '2026-06-05T02:00:00Z',
      stage: 0,
      actingForApproverId: 'emp-a',
    },
  ],
  stages: [
    { stageIndex: 0, approverId: 'emp-a', status: 'APPROVED' },
    { stageIndex: 1, approverId: 'emp-b', status: 'PENDING' },
  ],
  currentStage: 1,
  totalStages: 2,
  createdAt: '2026-06-05T00:00:00Z',
  submittedAt: '2026-06-05T01:00:00Z',
};

describe('ApprovalDetail — multi-stage timeline + IN_REVIEW + delegation', () => {
  it('renders IN_REVIEW badge on detail', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ data: MULTI_STAGE })));
    render(<ApprovalDetail id="appr-multi" onClose={() => {}} />, {
      wrapper: wrapper(),
    });
    await waitFor(() =>
      expect(screen.getByTestId('approval-detail')).toBeInTheDocument(),
    );
    await waitFor(() => {
      const badge = screen.getByTestId('approval-status-badge');
      expect(badge).toHaveAttribute('data-status', 'IN_REVIEW');
      expect(badge.textContent).toBe('검토중');
    });
  });

  it('renders the stage-progress timeline with currentStage highlighted', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ data: MULTI_STAGE })));
    render(<ApprovalDetail id="appr-multi" onClose={() => {}} />, {
      wrapper: wrapper(),
    });
    await waitFor(() =>
      expect(screen.getByTestId('approval-stages')).toBeInTheDocument(),
    );
    // Stage 0 approved (not current) → testid approval-stage-0.
    expect(screen.getByTestId('approval-stage-0')).toBeInTheDocument();
    // Stage 1 current → testid approval-stage-current.
    expect(screen.getByTestId('approval-stage-current')).toBeInTheDocument();
    // "현재" marker should be present.
    expect(screen.getByTestId('approval-stage-current').textContent).toContain('현재');
  });

  it('shows "k/N 단계" summary in the heading', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ data: MULTI_STAGE })));
    render(<ApprovalDetail id="appr-multi" onClose={() => {}} />, {
      wrapper: wrapper(),
    });
    await waitFor(() =>
      expect(screen.getByTestId('approval-stages')).toBeInTheDocument(),
    );
    // currentStage=1 → "2/2 단계" (1-based display of 0-based index).
    expect(screen.getByText(/2\/2 단계/)).toBeInTheDocument();
  });

  it('history shows stage annotation + delegation marker', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ data: MULTI_STAGE })));
    render(<ApprovalDetail id="appr-multi" onClose={() => {}} />, {
      wrapper: wrapper(),
    });
    await waitFor(() =>
      expect(screen.getByTestId('approval-history')).toBeInTheDocument(),
    );
    // History entry 1 has stage=0 and actingForApproverId=emp-a.
    // TASK-PC-FE-309 — actingForApproverId now resolves via useEmployee;
    // this fixture never seeds a matching employee detail response, so the
    // honest outcome is MASTER_REF_UNRESOLVED, never the raw id (276 Edge
    // Case — "id 로 조용히 되돌아가지 않는다").
    const delegated = await screen.findByTestId('approval-history-delegated-1');
    expect(delegated.textContent).toContain(MASTER_REF_UNRESOLVED);
    expect(delegated.textContent).toContain('대결');
    // Stage annotation visible in history entry 0.
    expect(screen.getByTestId('approval-history-0').textContent).toContain('1단계');
  });

  it('detail WITHOUT stages falls back to approverId (no crash — degrade)', async () => {
    // A legacy/single-stage response with NO stages field.
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ data: SUBMITTED })));
    render(<ApprovalDetail id="appr-sub" onClose={() => {}} />, {
      wrapper: wrapper(),
    });
    await waitFor(() =>
      expect(screen.getByTestId('approval-detail')).toBeInTheDocument(),
    );
    await waitFor(() =>
      // No stages timeline; falls back to single approverId display.
      expect(screen.queryByTestId('approval-stages')).not.toBeInTheDocument(),
    );
    // The fallback approverId is rendered. TASK-PC-FE-309 — it now resolves
    // via useEmployee; this fixture never seeds a matching employee detail
    // response, so the honest outcome is MASTER_REF_UNRESOLVED, never the
    // raw id.
    expect(screen.getByTestId('approval-approverId')).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByTestId('approval-approverId').textContent).toBe(
        MASTER_REF_UNRESOLVED,
      ),
    );
  });
});

// ===========================================================================
// deep-link preselect — /erp/approval?request=<id> (PC-FE-230).
// ===========================================================================

describe('ApprovalScreen — deep-link preselect (initialSelectedId)', () => {
  it('no initialSelectedId → list only, detail dialog closed', () => {
    render(<ApprovalScreen initialRequests={LIST} initialInbox={INBOX} />, {
      wrapper: wrapper(),
    });
    expect(screen.getByTestId('approval-list-table')).toBeInTheDocument();
    expect(screen.queryByTestId('approval-detail')).not.toBeInTheDocument();
  });

  it('initialSelectedId opens the target request detail on mount (over the list)', async () => {
    const fetchMock = vi.fn((url: string) => {
      if (String(url).includes('/requests/appr-1')) {
        return Promise.resolve(jsonResponse({ data: { ...SUBMITTED, id: 'appr-1' } }));
      }
      return Promise.resolve(jsonResponse(LIST));
    });
    vi.stubGlobal('fetch', fetchMock);
    render(
      <ApprovalScreen
        initialRequests={LIST}
        initialInbox={INBOX}
        initialSelectedId="appr-1"
      />,
      { wrapper: wrapper() },
    );
    // Detail dialog opened for the deep-linked id — and it fetched by that id.
    await screen.findByTestId('approval-detail');
    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some((c) =>
          String(c[0]).includes('/api/erp/approval/requests/appr-1'),
        ),
      ).toBe(true),
    );
    // The list is still rendered underneath (deep-link overlays, never replaces).
    expect(screen.getByTestId('approval-screen')).toBeInTheDocument();
  });

  it('unknown / stale initialSelectedId → graceful not-found over the still-rendered list (no crash)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string) =>
        String(url).includes('/requests/ghost')
          ? Promise.resolve(errorResponse('APPROVAL_REQUEST_NOT_FOUND', 404))
          : Promise.resolve(jsonResponse(LIST)),
      ),
    );
    render(
      <ApprovalScreen
        initialRequests={LIST}
        initialInbox={INBOX}
        initialSelectedId="ghost"
      />,
      { wrapper: wrapper() },
    );
    // Graceful inline not-found in the detail dialog — never a crash.
    const err = await screen.findByTestId('approval-detail-error');
    expect(err.textContent).toContain('찾을 수 없습니다');
    // The list screen is intact behind the notice.
    expect(screen.getByTestId('approval-screen')).toBeInTheDocument();
    expect(screen.getByTestId('approval-list-table')).toBeInTheDocument();
  });
});

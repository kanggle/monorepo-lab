import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';

/**
 * TASK-PC-FE-318 (`TASK-MONO-774` S4) — the employee ↔ IAM account link
 * screens:
 *   - AC-1  employee list / detail show «연결됨» / «연결된 계정 없음» (both
 *           states), never the raw account UUID as text;
 *   - AC-2  the HR dialog proposes through the same-origin proxy; the owner's
 *           card accepts / declines; `EMPLOYEE_LINK_SELF_ACCEPT` ends in
 *           «제안한 사람이 수락할 수 없습니다», `EMPLOYEE_LINK_NOT_ADDRESSEE` and
 *           each `EMPLOYEE_LINK_CONFLICT` cause read differently; a 503 never
 *           blames the user's data.
 * Same-origin fetch mocked; the real api-client parses the error body (so
 * `details.cause` travels the real client path).
 */

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: vi.fn() }),
  usePathname: () => '/erp',
  useSearchParams: () => new URLSearchParams(),
}));

import {
  EmployeeList,
  EmployeeDetail,
  EmployeeAccountLinkDialog,
  MyAccountLinkProposalsCard,
} from '@/features/erp-ops';
import type { Employee, EmployeeListResponse } from '@/features/erp-ops';
import { accountLinkErrorMessage } from '@/features/erp-ops/components/account-link-error';
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
function errorResponse(code: string, status: number, details?: unknown) {
  return new Response(
    JSON.stringify({ code, message: 'e', ...(details ? { details } : {}) }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const UUID_RE = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i;
const LINKED_ACCOUNT = '0199de70-0000-7000-8000-00000000ac01';
const PERIOD = { effectiveFrom: '2026-01-01', effectiveTo: null };

const LINKED: Employee = {
  id: 'emp-linked',
  employeeNumber: 'E-0001',
  name: '김연결',
  status: 'ACTIVE',
  employmentStatus: 'EMPLOYED',
  effectivePeriod: PERIOD,
  accountId: LINKED_ACCOUNT,
};
const UNLINKED: Employee = {
  id: 'emp-unlinked',
  employeeNumber: 'E-0002',
  name: '박미연결',
  status: 'ACTIVE',
  employmentStatus: 'EMPLOYED',
  effectivePeriod: PERIOD,
};
const LIST: EmployeeListResponse = {
  data: [LINKED, UNLINKED],
  meta: { page: 0, size: 20, totalElements: 2 },
};
const EMPTY_PAGE = { data: [], meta: { page: 0, size: 20, totalElements: 0 } };

beforeEach(() => {
  vi.unstubAllGlobals();
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(EMPTY_PAGE)));
});

// ===========================================================================
// AC-1 — «연결된 계정» in the list and the detail, both states.
// ===========================================================================

describe('AC-1 — employee list / detail «연결된 계정»', () => {
  it('list: linked row reads «연결됨», unlinked row reads «연결된 계정 없음»; the UUID is only in title', () => {
    render(<EmployeeList initial={LIST} />, { wrapper: wrapper() });
    const linked = screen.getByTestId('erp-employee-account-0');
    const unlinked = screen.getByTestId('erp-employee-account-1');
    expect(linked.textContent).toBe('연결됨');
    expect(linked).toHaveAttribute('data-linked', 'true');
    expect(linked.getAttribute('title')).toContain(LINKED_ACCOUNT);
    expect(unlinked.textContent).toBe('연결된 계정 없음');
    expect(unlinked).toHaveAttribute('data-linked', 'false');
    // TASK-PC-FE-309 discipline — no raw UUID drawn as text anywhere in the table.
    expect(UUID_RE.test(screen.getByTestId('erp-employees-table').textContent ?? '')).toBe(false);
  });

  it('detail: both states', () => {
    const { unmount } = render(<EmployeeDetail id={LINKED.id} initial={LINKED} />, {
      wrapper: wrapper(),
    });
    expect(screen.getByTestId('erp-employee-account').textContent).toBe('연결됨');
    unmount();
    render(<EmployeeDetail id={UNLINKED.id} initial={UNLINKED} />, { wrapper: wrapper() });
    expect(screen.getByTestId('erp-employee-account').textContent).toBe('연결된 계정 없음');
  });

  it('writable list offers «계정 연결» per row, opening the link dialog for THAT employee', async () => {
    const user = userEvent.setup();
    render(<EmployeeList initial={LIST} writable />, { wrapper: wrapper() });
    await user.click(screen.getByTestId('erp-employee-link-1'));
    const dialog = screen.getByTestId('erp-account-link-dialog');
    expect(dialog.textContent).toContain('E-0002 · 박미연결');
    expect(screen.getByTestId('erp-account-link-current').textContent).toBe('연결된 계정 없음');
  });

  it('read-only list offers no «계정 연결» button', () => {
    render(<EmployeeList initial={LIST} />, { wrapper: wrapper() });
    expect(screen.queryByTestId('erp-employee-link-0')).not.toBeInTheDocument();
  });
});

// ===========================================================================
// AC-2 — HR side: propose / revoke / unlink.
// ===========================================================================

describe('AC-2 — EmployeeAccountLinkDialog (HR side)', () => {
  it('propose → POST /employees/{id}/account-link-proposals with accountId + Idempotency-Key', async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (init?.method === 'POST') {
        return Promise.resolve(
          jsonResponse(
            {
              data: {
                id: 'prop-1',
                employeeId: UNLINKED.id,
                accountId: 'acc-B',
                status: 'PENDING',
                proposedBy: 'acc-A',
                proposedAt: '2026-10-07T00:00:00Z',
              },
            },
            201,
          ),
        );
      }
      return Promise.resolve(jsonResponse(EMPTY_PAGE));
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<EmployeeAccountLinkDialog employee={UNLINKED} onClose={vi.fn()} />, {
      wrapper: wrapper(),
    });
    expect(screen.getByTestId('erp-account-link-propose')).toBeDisabled();
    await user.type(screen.getByTestId('erp-account-link-account-id'), 'acc-B');
    await user.click(screen.getByTestId('erp-account-link-propose'));
    await screen.findByTestId('erp-account-link-propose-done');
    const call = fetchMock.mock.calls.find((c) => c[1]?.method === 'POST')!;
    expect(String(call[0])).toBe(
      `/api/erp/masterdata/employees/${UNLINKED.id}/account-link-proposals`,
    );
    const body = JSON.parse(String(call[1]!.body));
    expect(body.accountId).toBe('acc-B');
    expect(typeof body.idempotencyKey).toBe('string');
  });

  it('proposing the proposer\'s own account → 403 EMPLOYEE_LINK_SELF_ACCEPT, worded for the PROPOSE side', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((_u: string, init?: RequestInit) =>
        Promise.resolve(
          init?.method === 'POST'
            ? errorResponse('EMPLOYEE_LINK_SELF_ACCEPT', 403)
            : jsonResponse(EMPTY_PAGE),
        ),
      ),
    );
    const user = userEvent.setup();
    render(<EmployeeAccountLinkDialog employee={UNLINKED} onClose={vi.fn()} />, {
      wrapper: wrapper(),
    });
    await user.type(screen.getByTestId('erp-account-link-account-id'), 'acc-A');
    await user.click(screen.getByTestId('erp-account-link-propose'));
    const err = await screen.findByTestId('erp-account-link-propose-error');
    expect(err.textContent).toContain('자기 계정은 제안할 수 없습니다');
  });

  it('409 EMPLOYEE_LINK_CONFLICT is worded by details.cause (account_already_linked)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((_u: string, init?: RequestInit) =>
        Promise.resolve(
          init?.method === 'POST'
            ? errorResponse('EMPLOYEE_LINK_CONFLICT', 409, { cause: 'account_already_linked' })
            : jsonResponse(EMPTY_PAGE),
        ),
      ),
    );
    const user = userEvent.setup();
    render(<EmployeeAccountLinkDialog employee={UNLINKED} onClose={vi.fn()} />, {
      wrapper: wrapper(),
    });
    await user.type(screen.getByTestId('erp-account-link-account-id'), 'acc-B');
    await user.click(screen.getByTestId('erp-account-link-propose'));
    const err = await screen.findByTestId('erp-account-link-propose-error');
    expect(err.textContent).toContain('이 계정은 이미 이 회사의 다른 직원과 연결되어 있습니다');
  });

  it('a PENDING proposal shows revoke (reason-gated) instead of the propose form', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          data: [
            {
              id: 'prop-9',
              employeeId: UNLINKED.id,
              accountId: 'acc-B',
              status: 'PENDING',
              proposedBy: 'acc-A',
              proposedAt: '2026-10-07T00:00:00Z',
            },
          ],
          meta: { page: 0, size: 20, totalElements: 1 },
        }),
      ),
    );
    render(<EmployeeAccountLinkDialog employee={UNLINKED} onClose={vi.fn()} />, {
      wrapper: wrapper(),
    });
    await screen.findByTestId('erp-account-link-pending');
    expect(screen.queryByTestId('erp-account-link-propose-section')).not.toBeInTheDocument();
    expect(screen.getByTestId('erp-account-link-revoke')).toBeDisabled();
  });

  it('a linked employee offers unlink (reason-gated), not propose', () => {
    render(<EmployeeAccountLinkDialog employee={LINKED} onClose={vi.fn()} />, {
      wrapper: wrapper(),
    });
    expect(screen.getByTestId('erp-account-link-current').textContent).toBe('연결됨');
    expect(screen.getByTestId('erp-account-link-unlink')).toBeDisabled();
    expect(screen.queryByTestId('erp-account-link-propose-section')).not.toBeInTheDocument();
  });

  it('sample visitor: the propose button is visible and the server refusal (403 SAMPLE_READ_ONLY) is shown', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((_u: string, init?: RequestInit) =>
        Promise.resolve(
          init?.method === 'POST'
            ? errorResponse('SAMPLE_READ_ONLY', 403)
            : jsonResponse(EMPTY_PAGE),
        ),
      ),
    );
    const user = userEvent.setup();
    render(<EmployeeAccountLinkDialog employee={UNLINKED} onClose={vi.fn()} />, {
      wrapper: wrapper(),
    });
    await user.type(screen.getByTestId('erp-account-link-account-id'), 'acc-B');
    await user.click(screen.getByTestId('erp-account-link-propose'));
    const err = await screen.findByTestId('erp-account-link-propose-error');
    expect(err.textContent).toContain('샘플 화면에서는 실행되지 않습니다');
  });
});

// ===========================================================================
// AC-2 — owner side: «proposals addressed to me».
// ===========================================================================

const MINE = {
  data: [
    {
      id: 'prop-1',
      employeeId: 'emp-1',
      accountId: 'acc-B',
      status: 'PENDING',
      proposedBy: 'acc-A',
      proposedAt: '2026-10-07T00:00:00Z',
      reason: '입사',
      employeeName: '김연결',
      employeeNumber: 'E-0001',
    },
  ],
  meta: { page: 0, size: 20, totalElements: 1 },
};

function mineFetch(onPost: () => Response) {
  return vi.fn((_u: string, init?: RequestInit) =>
    Promise.resolve(init?.method === 'POST' ? onPost() : jsonResponse(MINE)),
  );
}

describe('AC-2 — MyAccountLinkProposalsCard (account owner)', () => {
  it('empty → «나에게 온 직원 연결 제안이 없습니다»', async () => {
    render(<MyAccountLinkProposalsCard />, { wrapper: wrapper() });
    expect(await screen.findByTestId('erp-my-link-proposals-empty')).toBeInTheDocument();
  });

  it('lists the proposal with the employee; accept → POST .../prop-1/accept with an Idempotency-Key', async () => {
    const fetchMock = mineFetch(() => jsonResponse({ data: { id: 'emp-1' } }));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<MyAccountLinkProposalsCard />, { wrapper: wrapper() });
    const row = await screen.findByTestId('erp-my-link-proposal-prop-1');
    expect(row.textContent).toContain('E-0001 · 김연결');
    await user.click(within(row).getByTestId('erp-my-link-proposal-accept-prop-1'));
    await waitFor(() =>
      expect(fetchMock.mock.calls.some((c) => c[1]?.method === 'POST')).toBe(true),
    );
    const call = fetchMock.mock.calls.find((c) => c[1]?.method === 'POST')!;
    expect(String(call[0])).toBe('/api/erp/masterdata/account-link-proposals/prop-1/accept');
    expect(typeof JSON.parse(String(call[1]!.body)).idempotencyKey).toBe('string');
  });

  it('🔴 the proposer accepting → «제안한 사람이 수락할 수 없습니다» (EMPLOYEE_LINK_SELF_ACCEPT)', async () => {
    vi.stubGlobal('fetch', mineFetch(() => errorResponse('EMPLOYEE_LINK_SELF_ACCEPT', 403)));
    const user = userEvent.setup();
    render(<MyAccountLinkProposalsCard />, { wrapper: wrapper() });
    await user.click(await screen.findByTestId('erp-my-link-proposal-accept-prop-1'));
    const err = await screen.findByTestId('erp-my-link-proposal-error-prop-1');
    expect(err.textContent).toContain('제안한 사람이 수락할 수 없습니다');
  });

  it('not the addressee → its own copy (not the two-person copy)', async () => {
    vi.stubGlobal('fetch', mineFetch(() => errorResponse('EMPLOYEE_LINK_NOT_ADDRESSEE', 403)));
    const user = userEvent.setup();
    render(<MyAccountLinkProposalsCard />, { wrapper: wrapper() });
    await user.click(await screen.findByTestId('erp-my-link-proposal-decline-prop-1'));
    const err = await screen.findByTestId('erp-my-link-proposal-error-prop-1');
    expect(err.textContent).toContain('내 계정 앞으로 온 제안이 아닙니다');
    expect(err.textContent).not.toContain('제안한 사람이 수락할 수 없습니다');
  });

  it('503 on accept → outage copy, never a statement about the user or the link', async () => {
    vi.stubGlobal('fetch', mineFetch(() => errorResponse('SERVICE_UNAVAILABLE', 503)));
    const user = userEvent.setup();
    render(<MyAccountLinkProposalsCard />, { wrapper: wrapper() });
    await user.click(await screen.findByTestId('erp-my-link-proposal-accept-prop-1'));
    const err = await screen.findByTestId('erp-my-link-proposal-error-prop-1');
    expect(err.textContent).toContain('일시적으로 사용할 수 없습니다');
    expect(err.textContent).not.toContain('연결');
  });

  it('a failed read degrades the card only (inline), no crash', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(errorResponse('SERVICE_UNAVAILABLE', 503)));
    render(<MyAccountLinkProposalsCard />, { wrapper: wrapper() });
    const err = await screen.findByTestId('erp-my-link-proposals-error');
    expect(err.textContent).toContain('일시적으로 사용할 수 없습니다');
  });
});

// ===========================================================================
// copy table — every link refusal reads differently.
// ===========================================================================

describe('accountLinkErrorMessage — distinct copy per refusal', () => {
  const msg = (code: string, status: number, details?: unknown, op: 'accept' | 'propose' = 'accept') =>
    accountLinkErrorMessage(new ApiError(status, code, 'm', undefined, details), op);

  it('each EMPLOYEE_LINK_CONFLICT cause + the other codes produce pairwise-distinct sentences', () => {
    const all = [
      msg('EMPLOYEE_LINK_SELF_ACCEPT', 403),
      msg('EMPLOYEE_LINK_SELF_ACCEPT', 403, undefined, 'propose'),
      msg('EMPLOYEE_LINK_NOT_ADDRESSEE', 403),
      msg('EMPLOYEE_LINK_CONFLICT', 409, { cause: 'employee_already_linked' }),
      msg('EMPLOYEE_LINK_CONFLICT', 409, { cause: 'account_already_linked' }),
      msg('EMPLOYEE_LINK_CONFLICT', 409, { cause: 'proposal_pending' }),
      msg('EMPLOYEE_LINK_CONFLICT', 409, { cause: 'proposal_not_pending' }),
      msg('EMPLOYEE_LINK_CONFLICT', 409, { cause: 'not_linked' }),
      msg('EMPLOYEE_LINK_CONFLICT', 409),
      msg('EMPLOYEE_LINK_INVALID', 422, { cause: 'employee_not_active' }),
      msg('EMPLOYEE_LINK_PROPOSAL_NOT_FOUND', 404),
      msg('SERVICE_UNAVAILABLE', 503),
    ];
    expect(new Set(all).size).toBe(all.length);
  });
});

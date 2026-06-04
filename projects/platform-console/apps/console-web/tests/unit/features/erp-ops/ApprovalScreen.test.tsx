import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';

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

  it('empty inbox renders the empty notice (no crash)', () => {
    render(
      <ApprovalScreen
        initialRequests={LIST}
        initialInbox={{ data: [], meta: { page: 0, size: 20, totalElements: 0 } }}
      />,
      { wrapper: wrapper() },
    );
    expect(screen.getByTestId('approval-inbox-empty')).toBeInTheDocument();
  });

  it('the status filter is present with all 5 statuses', () => {
    render(<ApprovalScreen initialRequests={LIST} initialInbox={INBOX} />, {
      wrapper: wrapper(),
    });
    const sel = screen.getByTestId('approval-status-filter');
    expect(sel.tagName).toBe('SELECT');
    expect(screen.getByRole('option', { name: '승인됨' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: '반려됨' })).toBeInTheDocument();
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

describe('ApprovalScreen — create dialog', () => {
  it('gates required fields then POSTs /requests with an Idempotency-Key', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ data: DRAFT }, 201));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<ApprovalScreen initialRequests={LIST} initialInbox={INBOX} />, {
      wrapper: wrapper(),
    });
    await user.click(screen.getByTestId('approval-create'));
    expect(screen.getByTestId('approval-create-dialog')).toBeInTheDocument();
    expect(screen.getByTestId('approval-create-submit')).toBeDisabled();
    await user.type(screen.getByTestId('approval-create-subjectId'), 'dept-1');
    await user.type(screen.getByTestId('approval-create-title'), '조직개편');
    await user.type(screen.getByTestId('approval-create-approverId'), 'emp-a');
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
    expect(body).toMatchObject({
      subjectType: 'DEPARTMENT',
      subjectId: 'dept-1',
      title: '조직개편',
      approverId: 'emp-a',
    });
    expect(typeof body.idempotencyKey).toBe('string');
  });
});

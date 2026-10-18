/**
 * TASK-PC-FE-347 — the ERP screens hide their mutation controls when `canWrite` is false and say
 * «조회 전용» (contract § 2.4.3.4 «Read-only sets hide write buttons»). The page wiring (which
 * roles make `canWrite` true) is pinned in `erp-write-buttons-pages.test.tsx`; the `permission-map`
 * rows in `console-nav-business-permission.test.ts`.
 *
 * Ticket scope pinned here:
 *   - `/erp/masters`   — 등록 · 수정 · 폐기 · 부서 이동 · 계정 연결 (5 master lists);
 *   - `/erp/approval`  — 결재 요청 작성 · 상신 · 승인 · 반려 · 회수;
 *   - `/erp/delegation`— 위임 생성 · 회수.
 * Failure Scenario 2: the approval target lookup (`GET /departments/{id}`, also opened by
 * `ERP_APPROVAL_WRITE`) is a READ — a read-only approver keeps the target display.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: vi.fn() }),
  usePathname: () => '/erp',
  useSearchParams: () => new URLSearchParams(),
}));

import {
  ErpMastersScreen,
  ErpApprovalScreen,
  ErpDelegationScreen,
} from '@/features/erp-ops';
import { ApprovalDetail } from '@/features/erp-ops/components/ApprovalScreen';

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const META = { page: 0, size: 20, totalElements: 1, timestamp: 'x' };
const PERIOD = { effectiveFrom: '2026-01-01T00:00:00Z', effectiveTo: null };
const list = (row: Record<string, unknown>) => ({ data: [row], meta: META }) as never;

// ===========================================================================
// /erp/masters
// ===========================================================================

const MASTER_ROWS = {
  initialDepartments: list({ id: 'd-1', code: 'D1', name: '운영본부', status: 'ACTIVE', effectivePeriod: PERIOD }),
  initialEmployees: list({ id: 'e-1', employeeNumber: 'E1', name: '김직원', departmentId: 'd-1', status: 'ACTIVE', effectivePeriod: PERIOD }),
  initialJobGrades: list({ id: 'j-1', code: 'J1', name: '과장', status: 'ACTIVE', effectivePeriod: PERIOD }),
  initialCostCenters: list({ id: 'c-1', code: 'C1', name: '비용센터', status: 'ACTIVE', effectivePeriod: PERIOD }),
  initialBusinessPartners: list({ id: 'b-1', code: 'B1', name: '거래처', partnerType: 'CUSTOMER', status: 'ACTIVE', effectivePeriod: PERIOD }),
};

const MASTER_WRITE_TESTIDS = [
  'erp-department-create', 'erp-department-edit-0', 'erp-department-move-0', 'erp-department-retire-0',
  'erp-employee-create', 'erp-employee-edit-0', 'erp-employee-retire-0', 'erp-employee-link-0',
  'erp-jobgrade-create', 'erp-jobgrade-edit-0', 'erp-jobgrade-retire-0',
  'erp-costcenter-create', 'erp-costcenter-edit-0', 'erp-costcenter-retire-0',
  'erp-businesspartner-create', 'erp-businesspartner-edit-0', 'erp-businesspartner-retire-0',
];

beforeEach(() => {
  vi.unstubAllGlobals();
  // no seeded detail/list → every lookup is a 404 (honest «이름 확인 불가», never a crash)
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json({ code: 'MASTERDATA_NOT_FOUND', message: 'e' }, 404)));
});

describe('TASK-PC-FE-347 — /erp/masters (ERP_MASTERDATA_WRITE)', () => {
  it('canWrite=false → none of the 17 write controls, and the «조회 전용» note', () => {
    render(<ErpMastersScreen {...MASTER_ROWS} canWrite={false} />, { wrapper: wrapper() });
    // the rows themselves are still there (read stays)
    expect(screen.getByTestId('erp-department-row-0')).toBeInTheDocument();
    expect(screen.getByTestId('erp-businesspartner-row-0')).toBeInTheDocument();
    for (const id of MASTER_WRITE_TESTIDS) expect(screen.queryByTestId(id), id).toBeNull();
    const note = screen.getByTestId('masters-read-only-note');
    expect(note).toHaveAttribute('role', 'note');
    expect(note).toHaveTextContent('조회 전용');
  });

  it('control group: canWrite=true → all 17 write controls, no note', () => {
    render(<ErpMastersScreen {...MASTER_ROWS} canWrite />, { wrapper: wrapper() });
    for (const id of MASTER_WRITE_TESTIDS) expect(screen.getByTestId(id), id).toBeInTheDocument();
    expect(screen.queryByTestId('masters-read-only-note')).toBeNull();
  });
});

// ===========================================================================
// /erp/approval
// ===========================================================================

const APPROVAL_ROW = {
  id: 'appr-1', status: 'SUBMITTED', subjectType: 'DEPARTMENT', subjectId: 'dept-1', title: '조직개편 결재',
  approverId: 'emp-a', submitterId: 'emp-s', createdAt: '2026-06-05T00:00:00Z', submittedAt: '2026-06-05T01:00:00Z',
};
const APPROVAL_LIST = { data: [APPROVAL_ROW], meta: { page: 0, size: 20, totalElements: 1, actorEmployeeId: 'emp-a' } } as never;
const ACTION_TESTIDS = [
  'approval-action-submit', 'approval-action-approve', 'approval-action-reject', 'approval-action-withdraw',
];
const DETAIL = (status: string) => ({
  id: 'appr-1', status, subjectType: 'DEPARTMENT', subjectId: 'dept-1', title: '조직개편 결재',
  approverId: 'emp-a', submitterId: 'emp-s', history: [], createdAt: '2026-06-05T00:00:00Z',
});

describe('TASK-PC-FE-347 — /erp/approval (ERP_APPROVAL_WRITE)', () => {
  it('canWrite=false → no 「결재 요청 작성」, the note, list and inbox still shown', () => {
    render(
      <ErpApprovalScreen initialApprovalRequests={APPROVAL_LIST} initialApprovalInbox={APPROVAL_LIST} canWrite={false} />,
      { wrapper: wrapper() },
    );
    expect(screen.queryByTestId('approval-create')).toBeNull();
    const note = screen.getByTestId('approval-read-only-note');
    expect(note).toHaveAttribute('role', 'note');
    expect(note).toHaveTextContent('조회 전용');
    expect(screen.getByTestId('approval-list-table')).toBeInTheDocument();
    expect(screen.getByTestId('approval-inbox-item-appr-1')).toBeInTheDocument();
    expect(screen.getByTestId('approval-open-0')).toBeInTheDocument();
  });

  it('control group: canWrite=true → 「결재 요청 작성」, no note', () => {
    render(
      <ErpApprovalScreen initialApprovalRequests={APPROVAL_LIST} initialApprovalInbox={APPROVAL_LIST} canWrite />,
      { wrapper: wrapper() },
    );
    expect(screen.getByTestId('approval-create')).toBeInTheDocument();
    expect(screen.queryByTestId('approval-read-only-note')).toBeNull();
  });

  it.each(['DRAFT', 'SUBMITTED', 'IN_REVIEW'])(
    'detail of a %s request, canWrite=false → none of 상신/승인/반려/회수, and not «완료된 결재입니다»',
    async (status) => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json({ data: DETAIL(status) })));
      render(<ApprovalDetail id="appr-1" onClose={() => {}} canWrite={false} />, { wrapper: wrapper() });
      await screen.findByTestId('approval-detail');
      await waitFor(() => expect(screen.queryByTestId('approval-detail-loading')).toBeNull());
      for (const id of ACTION_TESTIDS) expect(screen.queryByTestId(id), id).toBeNull();
      // a missing button is not «finished»: that sentence is for terminal states only
      expect(screen.queryByTestId('approval-no-actions')).toBeNull();
      expect(screen.getByTestId('approval-detail-read-only-note')).toHaveTextContent('조회 전용');
      expect(screen.getByTestId('approval-detail-close')).toBeInTheDocument();
    },
  );

  it('control group: detail SUBMITTED, canWrite=true → 승인/반려/회수, no note', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json({ data: DETAIL('SUBMITTED') })));
    render(<ApprovalDetail id="appr-1" onClose={() => {}} canWrite />, { wrapper: wrapper() });
    await screen.findByTestId('approval-action-approve');
    expect(screen.getByTestId('approval-action-reject')).toBeInTheDocument();
    expect(screen.getByTestId('approval-action-withdraw')).toBeInTheDocument();
    expect(screen.queryByTestId('approval-detail-read-only-note')).toBeNull();
  });

  it('control group: a finished request still says «완료된 결재입니다» when canWrite=true', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json({ data: DETAIL('APPROVED') })));
    render(<ApprovalDetail id="appr-1" onClose={() => {}} canWrite />, { wrapper: wrapper() });
    expect(await screen.findByTestId('approval-no-actions')).toBeInTheDocument();
  });

  it('🔴 Failure Scenario 2: canWrite=false still looks the approval target up (a READ, not gated)', async () => {
    const fetchMock = vi.fn().mockImplementation(async (url: string) =>
      String(url).includes('/api/erp/masterdata/departments/dept-1')
        ? json({ data: { id: 'dept-1', code: 'DEPT-OPS', name: '운영본부', status: 'ACTIVE', effectivePeriod: PERIOD }, meta: META })
        : json({ code: 'MASTERDATA_NOT_FOUND', message: 'e' }, 404),
    );
    vi.stubGlobal('fetch', fetchMock);
    render(
      <ErpApprovalScreen initialApprovalRequests={APPROVAL_LIST} initialApprovalInbox={APPROVAL_LIST} canWrite={false} />,
      { wrapper: wrapper() },
    );
    await waitFor(() =>
      expect(fetchMock.mock.calls.map((c) => String(c[0]))).toContainEqual(
        expect.stringContaining('/api/erp/masterdata/departments/dept-1'),
      ),
    );
    // the subject cell is still the approval list's own cell (not removed with the buttons)
    expect(within(screen.getByTestId('approval-list-table')).getByText(/부서 ·/)).toBeInTheDocument();
  });
});

// ===========================================================================
// /erp/delegation
// ===========================================================================

const GRANT = {
  id: 'del-1', delegatorId: 'emp-me', delegateId: 'emp-x', validFrom: '2026-06-01T00:00:00Z',
  status: 'ACTIVE', createdAt: '2026-06-01T00:00:00Z', createdBy: 'emp-me',
};
function delegationFetch() {
  return vi.fn().mockImplementation(async (url: string) => {
    const u = String(url);
    if (u.includes('/api/erp/approval/delegations')) {
      return json({ data: [GRANT], meta: { page: 0, size: 20, totalElements: 1 } });
    }
    return json({ data: [], meta: { page: 0, size: 20, totalElements: 0 } });
  });
}

describe('TASK-PC-FE-347 — /erp/delegation (ERP_APPROVAL_WRITE)', () => {
  it('canWrite=false → no 「위임 생성」 and no 「회수」, the grant row and its badge stay, the note shows', async () => {
    vi.stubGlobal('fetch', delegationFetch());
    render(<ErpDelegationScreen canWrite={false} />, { wrapper: wrapper() });
    await screen.findAllByTestId('delegation-row-del-1');
    expect(screen.queryByTestId('delegation-create')).toBeNull();
    expect(screen.queryByTestId('delegation-revoke-del-1')).toBeNull();
    expect(screen.getAllByTestId('delegation-status-badge').length).toBeGreaterThan(0);
    const note = screen.getByTestId('delegation-read-only-note');
    expect(note).toHaveAttribute('role', 'note');
    expect(note).toHaveTextContent('조회 전용');
  });

  it('control group: canWrite=true → 「위임 생성」 and 「회수」, no note', async () => {
    vi.stubGlobal('fetch', delegationFetch());
    render(<ErpDelegationScreen canWrite />, { wrapper: wrapper() });
    await screen.findByTestId('delegation-revoke-del-1');
    expect(screen.getByTestId('delegation-create')).toBeInTheDocument();
    expect(screen.queryByTestId('delegation-read-only-note')).toBeNull();
  });
});

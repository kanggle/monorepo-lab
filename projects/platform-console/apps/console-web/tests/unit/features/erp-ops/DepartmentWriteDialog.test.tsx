import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
// Mock next/navigation so DepartmentList's useDepartments → useAsOf
// hook mounts in the jsdom env (mirrors ErpOpsScreen.test.tsx).
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: vi.fn() }),
  usePathname: () => '/erp',
  useSearchParams: () => new URLSearchParams(),
}));

import {
  DepartmentWriteDialog,
  DepartmentList,
  type Department,
} from '@/features/erp-ops';

/**
 * TASK-PC-FE-046 — department write PILOT UI. The dialog clones the
 * operators reason+confirm+Idempotency-Key pattern adapted to the erp
 * producer contract: create/update have NO reason field (no producer
 * slot); retire requires a typed reason (producer slot). DepartmentList
 * exposes the write affordances ONLY when `writable` (the four other
 * masters never get them). Same-origin `/api/erp/masterdata/...` fetch
 * mocked.
 */

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

const DEPT: Department = {
  id: 'dept-1',
  code: 'DEPT-001',
  name: 'Sales',
  parentId: null,
  status: 'ACTIVE',
  effectivePeriod: { effectiveFrom: '2026-01-01', effectiveTo: null },
};

function deptResponse(): Response {
  return new Response(JSON.stringify(DEPT), {
    status: 201,
    headers: { 'Content-Type': 'application/json' },
  });
}

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe('DepartmentWriteDialog (부서 write PILOT)', () => {
  it('create: shows code+name, NO reason field; submit gated on code+name; POSTs departments', async () => {
    const fetchMock = vi.fn().mockResolvedValue(deptResponse());
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(
      <DepartmentWriteDialog request={{ mode: 'create' }} onClose={() => {}} />,
      { wrapper: wrapper() },
    );

    // create has NO reason field (no producer slot).
    expect(screen.queryByTestId('erp-dept-reason')).not.toBeInTheDocument();
    // gated until code + name present.
    expect(screen.getByTestId('erp-dept-write-submit')).toBeDisabled();

    await user.type(screen.getByTestId('erp-dept-code'), 'DEPT-001');
    await user.type(screen.getByTestId('erp-dept-name'), 'Sales');
    expect(screen.getByTestId('erp-dept-write-submit')).toBeEnabled();

    await user.click(screen.getByTestId('erp-dept-write-submit'));
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/erp/masterdata/departments',
        expect.objectContaining({ method: 'POST' }),
      ),
    );
    const body = JSON.parse(
      (fetchMock.mock.calls[0][1] as RequestInit).body as string,
    );
    expect(body).toMatchObject({ code: 'DEPT-001', name: 'Sales' });
    expect(typeof body.idempotencyKey).toBe('string');
    expect(body.idempotencyKey.length).toBeGreaterThan(0);
  });

  it('retire: requires a typed reason before confirm enables; POSTs .../retire with reason', async () => {
    const fetchMock = vi.fn().mockResolvedValue(deptResponse());
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(
      <DepartmentWriteDialog
        request={{ mode: 'retire', target: DEPT }}
        onClose={() => {}}
      />,
      { wrapper: wrapper() },
    );

    expect(screen.getByTestId('erp-dept-reason')).toBeInTheDocument();
    expect(screen.getByTestId('erp-dept-write-submit')).toBeDisabled();
    expect(screen.getByTestId('erp-dept-reason-error')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();

    await user.type(screen.getByTestId('erp-dept-reason'), '조직 개편');
    expect(screen.getByTestId('erp-dept-write-submit')).toBeEnabled();
    await user.click(screen.getByTestId('erp-dept-write-submit'));
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/erp/masterdata/departments/dept-1/retire',
        expect.objectContaining({ method: 'POST' }),
      ),
    );
    const body = JSON.parse(
      (fetchMock.mock.calls[0][1] as RequestInit).body as string,
    );
    expect(body.reason).toBe('조직 개편');
  });

  it('move-parent: reason optional (submit enabled with only effectiveFrom)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(deptResponse());
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(
      <DepartmentWriteDialog
        request={{ mode: 'move-parent', target: DEPT }}
        onClose={() => {}}
      />,
      { wrapper: wrapper() },
    );
    // no reason typed → still disabled because effectiveFrom required.
    expect(screen.getByTestId('erp-dept-write-submit')).toBeDisabled();
    await user.type(screen.getByTestId('erp-dept-effective-from'), '2026-07-01');
    // reason NOT required for move-parent → enabled now.
    expect(screen.getByTestId('erp-dept-write-submit')).toBeEnabled();
  });
});

describe('DepartmentWriteDialog — parent picker dropdown (TASK-PC-FE-047)', () => {
  const SALES: Department = {
    id: 'dept-sales',
    code: 'SALES',
    name: '영업본부',
    parentId: null,
    status: 'ACTIVE',
    effectivePeriod: { effectiveFrom: '2026-01-01', effectiveTo: null },
  };
  const RETIRED: Department = {
    id: 'dept-old',
    code: 'OLD',
    name: '폐지본부',
    parentId: null,
    status: 'RETIRED',
    effectivePeriod: { effectiveFrom: '2025-01-01', effectiveTo: '2025-12-31' },
  };

  it('create: parent is a <select> listing active departments (retired excluded); selecting one sends parentId', async () => {
    const fetchMock = vi.fn().mockResolvedValue(deptResponse());
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(
      <DepartmentWriteDialog
        request={{ mode: 'create' }}
        onClose={() => {}}
        departments={[SALES, RETIRED]}
      />,
      { wrapper: wrapper() },
    );

    const select = screen.getByTestId('erp-dept-parent-id') as HTMLSelectElement;
    expect(select.tagName).toBe('SELECT');
    // active department is an option; retired is excluded.
    expect(
      screen.getByRole('option', { name: /SALES · 영업본부/ }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('option', { name: /OLD · 폐지본부/ }),
    ).not.toBeInTheDocument();

    await user.type(screen.getByTestId('erp-dept-code'), 'TEAM-A');
    await user.type(screen.getByTestId('erp-dept-name'), 'A팀');
    await user.selectOptions(select, 'dept-sales');
    await user.click(screen.getByTestId('erp-dept-write-submit'));
    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const body = JSON.parse(
      (fetchMock.mock.calls[0][1] as RequestInit).body as string,
    );
    expect(body.parentId).toBe('dept-sales');
  });

  it('move-parent: the dropdown excludes the target department itself', () => {
    render(
      <DepartmentWriteDialog
        request={{ mode: 'move-parent', target: SALES }}
        onClose={() => {}}
        departments={[SALES, DEPT]}
      />,
      { wrapper: wrapper() },
    );
    const select = screen.getByTestId('erp-dept-new-parent-id');
    expect(select.tagName).toBe('SELECT');
    // the OTHER department is selectable; the target itself is NOT.
    expect(
      screen.getByRole('option', { name: /DEPT-001/ }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('option', { name: /SALES · 영업본부/ }),
    ).not.toBeInTheDocument();
  });
});

describe('DepartmentList — writable gate (only the department master gets write)', () => {
  const INITIAL = {
    data: [DEPT],
    meta: { page: 0, size: 20, totalElements: 1, timestamp: 'x' },
  };

  it('writable: shows "부서 추가" + per-row edit/move/retire actions', () => {
    render(<DepartmentList initial={INITIAL} writable />, {
      wrapper: wrapper(),
    });
    expect(screen.getByTestId('erp-department-create')).toBeInTheDocument();
    expect(screen.getByTestId('erp-department-edit-0')).toBeInTheDocument();
    expect(screen.getByTestId('erp-department-move-0')).toBeInTheDocument();
    expect(screen.getByTestId('erp-department-retire-0')).toBeInTheDocument();
  });

  it('NOT writable (default): no write affordance at all (read-only parity with the other masters)', () => {
    render(<DepartmentList initial={INITIAL} />, { wrapper: wrapper() });
    expect(screen.queryByTestId('erp-department-create')).not.toBeInTheDocument();
    expect(screen.queryByTestId('erp-department-edit-0')).not.toBeInTheDocument();
    expect(screen.queryByTestId('erp-department-retire-0')).not.toBeInTheDocument();
    // the read table still renders.
    expect(screen.getByTestId('erp-departments-table')).toBeInTheDocument();
  });

  it('opening "부서 추가" mounts the write dialog in create mode', async () => {
    const user = userEvent.setup();
    render(<DepartmentList initial={INITIAL} writable />, {
      wrapper: wrapper(),
    });
    await user.click(screen.getByTestId('erp-department-create'));
    const dialog = screen.getByTestId('erp-dept-write-dialog');
    expect(dialog).toHaveAttribute('data-mode', 'create');
  });
});

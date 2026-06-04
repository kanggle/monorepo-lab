import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';

/**
 * TASK-PC-FE-048 — the generic master write dialog + the four lists' writable
 * gates (employees / job-grades / cost-centers / business-partners). Same
 * confirm+reason+Idempotency-Key discipline as the department pilot, driven by
 * a field config. FK fields render as dropdowns from the section's loaded
 * lists. Same-origin `/api/erp/masterdata/...` fetch mocked.
 */

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: vi.fn() }),
  usePathname: () => '/erp',
  useSearchParams: () => new URLSearchParams(),
}));

import {
  JobGradeList,
  EmployeeList,
  type JobGrade,
} from '@/features/erp-ops';

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

const JG: JobGrade = {
  id: 'jg-1',
  code: 'G3',
  name: '사원',
  displayOrder: 30,
  status: 'ACTIVE',
  effectivePeriod: { effectiveFrom: '2026-01-01', effectiveTo: null },
};
const JG_LIST = {
  data: [JG],
  meta: { page: 0, size: 20, totalElements: 1, timestamp: 'x' },
};

function jobGradeResponse(): Response {
  return new Response(JSON.stringify(JG), {
    status: 201,
    headers: { 'Content-Type': 'application/json' },
  });
}

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe('JobGradeList — writable gate', () => {
  it('writable: shows 직급 추가 + per-row 수정/폐기', () => {
    render(<JobGradeList initial={JG_LIST} writable />, { wrapper: wrapper() });
    expect(screen.getByTestId('erp-jobgrade-create')).toBeInTheDocument();
    expect(screen.getByTestId('erp-jobgrade-edit-0')).toBeInTheDocument();
    expect(screen.getByTestId('erp-jobgrade-retire-0')).toBeInTheDocument();
  });

  it('NOT writable (default): no write affordance', () => {
    render(<JobGradeList initial={JG_LIST} />, { wrapper: wrapper() });
    expect(screen.queryByTestId('erp-jobgrade-create')).not.toBeInTheDocument();
    expect(screen.queryByTestId('erp-jobgrade-edit-0')).not.toBeInTheDocument();
    expect(screen.getByTestId('erp-jobgrades-table')).toBeInTheDocument();
  });
});

describe('MasterWriteDialog (generic) via JobGradeList', () => {
  it('create: required-field gate + POSTs the collection with idempotency', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jobGradeResponse());
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<JobGradeList initial={JG_LIST} writable />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('erp-jobgrade-create'));
    const dialog = screen.getByTestId('erp-jobgrade-write-dialog');
    expect(dialog).toHaveAttribute('data-mode', 'create');
    // gated until required code + name.
    expect(screen.getByTestId('erp-jobgrade-write-submit')).toBeDisabled();

    await user.type(screen.getByTestId('erp-jobgrade-field-code'), 'G4');
    await user.type(screen.getByTestId('erp-jobgrade-field-name'), '대리');
    expect(screen.getByTestId('erp-jobgrade-write-submit')).toBeEnabled();

    await user.click(screen.getByTestId('erp-jobgrade-write-submit'));
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/erp/masterdata/job-grades',
        expect.objectContaining({ method: 'POST' }),
      ),
    );
    const body = JSON.parse(
      (fetchMock.mock.calls[0][1] as RequestInit).body as string,
    );
    expect(body).toMatchObject({ code: 'G4', name: '대리' });
    expect(typeof body.idempotencyKey).toBe('string');
  });

  it('retire: requires a reason; POSTs .../{id}/retire with reason', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jobGradeResponse());
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<JobGradeList initial={JG_LIST} writable />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('erp-jobgrade-retire-0'));
    expect(screen.getByTestId('erp-jobgrade-write-dialog')).toHaveAttribute(
      'data-mode',
      'retire',
    );
    expect(screen.getByTestId('erp-jobgrade-write-submit')).toBeDisabled();
    await user.type(screen.getByTestId('erp-jobgrade-reason'), '직급 개편');
    expect(screen.getByTestId('erp-jobgrade-write-submit')).toBeEnabled();
    await user.click(screen.getByTestId('erp-jobgrade-write-submit'));
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/erp/masterdata/job-grades/jg-1/retire',
        expect.objectContaining({ method: 'POST' }),
      ),
    );
    const body = JSON.parse(
      (fetchMock.mock.calls[0][1] as RequestInit).body as string,
    );
    expect(body.reason).toBe('직급 개편');
  });
});

describe('EmployeeList — FK dropdowns from optionSources', () => {
  const EMP_LIST = {
    data: [
      {
        id: 'emp-1',
        employeeNumber: 'E-1',
        name: '홍길동',
        status: 'ACTIVE',
        employmentStatus: 'EMPLOYED',
        effectivePeriod: { effectiveFrom: '2026-01-01', effectiveTo: null },
      },
    ],
    meta: { page: 0, size: 20, totalElements: 1, timestamp: 'x' },
  };

  it('create dialog renders the 부서 FK as a dropdown listing the provided departments', async () => {
    const user = userEvent.setup();
    render(
      <EmployeeList
        initial={EMP_LIST}
        writable
        optionSources={{
          departments: [{ id: 'dept-1', code: 'SALES', name: '영업본부' }],
        }}
      />,
      { wrapper: wrapper() },
    );
    await user.click(screen.getByTestId('erp-employee-create'));
    const deptSelect = screen.getByTestId('erp-employee-field-departmentId');
    expect(deptSelect.tagName).toBe('SELECT');
    expect(
      screen.getByRole('option', { name: /SALES · 영업본부/ }),
    ).toBeInTheDocument();
    // required fields gate.
    expect(screen.getByTestId('erp-employee-write-submit')).toBeDisabled();
  });
});

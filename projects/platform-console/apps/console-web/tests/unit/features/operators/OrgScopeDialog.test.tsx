import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';

// useDepartments → useAsOf reads next/navigation; mount stubs (mirror
// DepartmentWriteDialog.test.tsx).
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: vi.fn() }),
  usePathname: () => '/operators',
  useSearchParams: () => new URLSearchParams(),
}));

import { OrgScopeDialog } from '@/features/operators';

/**
 * TASK-PC-FE-050 — org_scope (데이터-스코프) dialog. tri-state
 * (전체/선택/차단 → null/[ids]/[]), reason-gating, department picker (active
 * only, retired excluded, selection → payload), degrade (departments fetch
 * fail → manual fallback + warning), no-assignment (empty → guidance + Save
 * disabled). All endpoints go through the same-origin proxy → a single
 * fetch mock routed by URL.
 */

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const DEPTS = {
  data: [
    {
      id: 'dept-sales',
      code: 'SALES',
      name: '영업본부',
      parentId: null,
      status: 'ACTIVE',
      effectivePeriod: { effectiveFrom: '2026-01-01', effectiveTo: null },
    },
    {
      id: 'dept-eng',
      code: 'ENG',
      name: '개발본부',
      parentId: null,
      status: 'ACTIVE',
      effectivePeriod: { effectiveFrom: '2026-01-01', effectiveTo: null },
    },
    {
      id: 'dept-old',
      code: 'OLD',
      name: '폐지본부',
      parentId: null,
      status: 'RETIRED',
      effectivePeriod: { effectiveFrom: '2025-01-01', effectiveTo: '2025-12-31' },
    },
  ],
  meta: { page: 0, size: 200, totalElements: 3, timestamp: 'x' },
};

/**
 * Routes the fetch mock by URL:
 *   - GET  /api/operators/{id}/assignments        → `assignments`
 *   - GET  /api/erp/masterdata/departments...      → `departments` (or fail)
 *   - PUT  /api/operators/{id}/assignments/.../org-scope → captured PUT
 */
function setupFetch(opts: {
  assignments: unknown;
  departments?: unknown;
  departmentsFail?: boolean;
  putResult?: unknown;
}) {
  const putCalls: Array<{ url: string; init: RequestInit }> = [];
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    const u = String(url);
    const method = (init?.method ?? 'GET').toUpperCase();
    if (u.includes('/assignments/') && u.includes('/org-scope')) {
      putCalls.push({ url: u, init: init ?? {} });
      return json(opts.putResult ?? { tenantId: 'acme-corp' });
    }
    if (u.includes('/assignments')) {
      return json(opts.assignments);
    }
    if (u.includes('/api/erp/masterdata/departments')) {
      if (opts.departmentsFail) return json({ code: 'ERP_NOT_ELIGIBLE' }, 503);
      return json(opts.departments ?? DEPTS);
    }
    void method;
    return json({}, 404);
  });
  vi.stubGlobal('fetch', fetchMock);
  return { fetchMock, putCalls };
}

beforeEach(() => {
  vi.unstubAllGlobals();
});

const PROPS = {
  open: true as const,
  operatorId: 'op-1',
  operatorLabel: 'admin@acme.test',
  onClose: () => {},
};

describe('OrgScopeDialog — current scope + tri-state payloads', () => {
  it('전체 (null assignment) → 전체 radio default; Save with reason → PUT { orgScope: null }', async () => {
    const { putCalls } = setupFetch({
      assignments: { assignments: [{ tenantId: 'acme-corp' }] }, // orgScope omitted ⇒ null
    });
    const user = userEvent.setup();
    render(<OrgScopeDialog {...PROPS} />, { wrapper: wrapper() });

    await waitFor(() =>
      expect(screen.getByTestId('org-scope-current')).toHaveTextContent(
        /net-zero/,
      ),
    );
    expect(screen.getByTestId('org-scope-mode-all')).toBeChecked();

    // Save disabled without reason.
    expect(screen.getByTestId('org-scope-save')).toBeDisabled();
    await user.type(screen.getByTestId('org-scope-reason'), 'full scope');
    expect(screen.getByTestId('org-scope-save')).toBeEnabled();
    await user.click(screen.getByTestId('org-scope-save'));

    await waitFor(() => expect(putCalls.length).toBe(1));
    const body = JSON.parse(putCalls[0].init.body as string);
    expect(body.orgScope).toBeNull();
    expect(body.reason).toBe('full scope');
  });

  it('차단 (block) → warning + confirm gate; Save → PUT { orgScope: [] }', async () => {
    const { putCalls } = setupFetch({
      assignments: { assignments: [{ tenantId: 'acme-corp' }] },
    });
    const user = userEvent.setup();
    render(<OrgScopeDialog {...PROPS} />, { wrapper: wrapper() });

    await waitFor(() => screen.getByTestId('org-scope-mode-block'));
    await user.click(screen.getByTestId('org-scope-mode-block'));
    expect(screen.getByTestId('org-scope-block-warning')).toBeInTheDocument();

    await user.type(screen.getByTestId('org-scope-reason'), 'lock out');
    // still disabled — block requires explicit confirm.
    expect(screen.getByTestId('org-scope-save')).toBeDisabled();
    await user.click(screen.getByTestId('org-scope-block-confirm'));
    expect(screen.getByTestId('org-scope-save')).toBeEnabled();
    await user.click(screen.getByTestId('org-scope-save'));

    await waitFor(() => expect(putCalls.length).toBe(1));
    const body = JSON.parse(putCalls[0].init.body as string);
    expect(body.orgScope).toEqual([]);
  });

  it('선택 부서 (subset) → active depts shown (retired excluded); selection → PUT { orgScope: [ids] }', async () => {
    const { putCalls } = setupFetch({
      assignments: { assignments: [{ tenantId: 'acme-corp' }] },
    });
    const user = userEvent.setup();
    render(<OrgScopeDialog {...PROPS} />, { wrapper: wrapper() });

    await waitFor(() => screen.getByTestId('org-scope-mode-subset'));
    await user.click(screen.getByTestId('org-scope-mode-subset'));

    // active departments rendered with `code · name`; retired excluded.
    await waitFor(() => screen.getByTestId('org-scope-dept-dept-sales'));
    expect(screen.getByTestId('org-scope-dept-dept-eng')).toBeInTheDocument();
    expect(
      screen.queryByTestId('org-scope-dept-dept-old'),
    ).not.toBeInTheDocument();
    expect(screen.getByText(/SALES · 영업본부/)).toBeInTheDocument();

    // empty selection → Save gated.
    await user.type(screen.getByTestId('org-scope-reason'), 'scope it');
    expect(screen.getByTestId('org-scope-save')).toBeDisabled();
    expect(screen.getByTestId('org-scope-subset-empty')).toBeInTheDocument();

    await user.click(screen.getByTestId('org-scope-dept-dept-sales'));
    expect(screen.getByTestId('org-scope-save')).toBeEnabled();
    await user.click(screen.getByTestId('org-scope-save'));

    await waitFor(() => expect(putCalls.length).toBe(1));
    const body = JSON.parse(putCalls[0].init.body as string);
    expect(body.orgScope).toEqual(['dept-sales']);
  });

  it('current explicit [] (차단) renders the 차단 summary + pre-selects block mode', async () => {
    setupFetch({
      assignments: { assignments: [{ tenantId: 'acme-corp', orgScope: [] }] },
    });
    render(<OrgScopeDialog {...PROPS} />, { wrapper: wrapper() });
    await waitFor(() =>
      expect(screen.getByTestId('org-scope-current')).toHaveTextContent(
        /zero-scope/,
      ),
    );
    // The block radio's checked state is derived in a separate render cycle
    // from the summary text above; assert it via waitFor too so a one-tick lag
    // under a slow/parallel CI runner can't flake this ("not checked"). The
    // summary-text waitFor alone did not gate the radio's settle.
    await waitFor(() =>
      expect(screen.getByTestId('org-scope-mode-block')).toBeChecked(),
    );
  });
});

describe('OrgScopeDialog — degrade + no-assignment', () => {
  it('departments fetch fail → manual id fallback + warning banner', async () => {
    const { putCalls } = setupFetch({
      assignments: { assignments: [{ tenantId: 'acme-corp' }] },
      departmentsFail: true,
    });
    const user = userEvent.setup();
    render(<OrgScopeDialog {...PROPS} />, { wrapper: wrapper() });

    await waitFor(() => screen.getByTestId('org-scope-mode-subset'));
    await user.click(screen.getByTestId('org-scope-mode-subset'));

    // degraded → manual textarea + warning, NOT a whole-dialog failure.
    await waitFor(() =>
      expect(screen.getByTestId('org-scope-depts-degraded')).toBeInTheDocument(),
    );
    expect(screen.getByTestId('org-scope-manual-input')).toBeInTheDocument();
    expect(screen.getByTestId('org-scope-dialog')).toBeInTheDocument();

    await user.type(
      screen.getByTestId('org-scope-manual-input'),
      'dept-sales\ndept-eng',
    );
    await user.type(screen.getByTestId('org-scope-reason'), 'manual scope');
    await user.click(screen.getByTestId('org-scope-save'));
    await waitFor(() => expect(putCalls.length).toBe(1));
    const body = JSON.parse(putCalls[0].init.body as string);
    expect(body.orgScope).toEqual(['dept-sales', 'dept-eng']);
  });

  it('no assignment row (empty array) → guidance + Save disabled (no PUT)', async () => {
    const { putCalls } = setupFetch({ assignments: { assignments: [] } });
    render(<OrgScopeDialog {...PROPS} />, { wrapper: wrapper() });

    await waitFor(() =>
      expect(screen.getByTestId('org-scope-no-assignment')).toBeInTheDocument(),
    );
    // no reason field / no tri-state when unassigned; Save disabled.
    expect(screen.getByTestId('org-scope-save')).toBeDisabled();
    expect(screen.queryByTestId('org-scope-reason')).not.toBeInTheDocument();
    expect(putCalls.length).toBe(0);
  });
});
